/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.ExecutionId;
import graphql.schema.DataFetchingEnvironment;
import io.micrometer.context.ContextSnapshot;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;

/**
 * {@link DataFetcherExceptionHandler} that invokes {@link DataFetcherExceptionResolver}'s
 * in a sequence until one returns a list of {@link GraphQLError}'s.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class ExceptionResolversExceptionHandler implements DataFetcherExceptionHandler {

	private static final Log logger = LogFactory.getLog(ExceptionResolversExceptionHandler.class);

	private final List<DataFetcherExceptionResolver> resolvers;

	/**
	 * Create an instance.
	 * @param resolvers the resolvers to use
	 */
	ExceptionResolversExceptionHandler(List<DataFetcherExceptionResolver> resolvers) {
		Assert.notNull(resolvers, "'resolvers' is required");
		this.resolvers = new ArrayList<>(resolvers);
	}

	@Override
	@Deprecated
	public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
		// This is not expected to be called but needs to be implemented until removed:
		// https://github.com/graphql-java/graphql-java/issues/2545
		throw new UnsupportedOperationException();
	}

	@Override
	public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters params) {
		Throwable exception = unwrapException(params);
		DataFetchingEnvironment env = params.getDataFetchingEnvironment();
		ContextSnapshot snapshot = ContextSnapshot.captureFrom(env.getGraphQlContext());
		try {
			return Flux.fromIterable(this.resolvers)
					.flatMap(resolver -> resolver.resolveException(exception, env))
					.map(errors -> DataFetcherExceptionHandlerResult.newResult().errors(errors).build())
					.next()
					.doOnNext(result -> logResolvedException(exception, result))
					.onErrorResume(resolverEx -> Mono.just(handleResolverError(resolverEx, exception, env)))
					.switchIfEmpty(Mono.fromCallable(() -> createInternalError(exception, env)))
					.contextWrite(snapshot::updateContext)
					.toFuture();
		}
		catch (Exception resolverEx) {
			return CompletableFuture.completedFuture(handleResolverError(resolverEx, exception, env));
		}
	}

	private DataFetcherExceptionHandlerResult handleResolverError(
			Throwable resolverException, Throwable originalException, DataFetchingEnvironment environment) {

		if (logger.isWarnEnabled()) {
			logger.warn("Failure while resolving " + originalException.getMessage(), resolverException);
		}
		return createInternalError(originalException, environment);
	}

	private Throwable unwrapException(DataFetcherExceptionHandlerParameters params) {
		Throwable ex = params.getException();
		return ((ex instanceof CompletionException) ? ex.getCause() : ex);
	}

	private void logResolvedException(Throwable ex, DataFetcherExceptionHandlerResult result) {
		if (logger.isDebugEnabled()) {
			logger.debug("Resolved " + ex.getClass().getSimpleName() +
					" to GraphQL error(s): " + result.getErrors(), ex);
		}
	}

	private DataFetcherExceptionHandlerResult createInternalError(Throwable ex, DataFetchingEnvironment environment) {
		ExecutionId executionId = environment.getExecutionId();
		if (logger.isErrorEnabled()) {
			logger.error("Unresolved " + ex.getClass().getSimpleName() + " for executionId " + executionId, ex);
		}
		return DataFetcherExceptionHandlerResult
				.newResult(GraphqlErrorBuilder.newError(environment)
						.errorType(ErrorType.INTERNAL_ERROR)
						.message(ErrorType.INTERNAL_ERROR + " for " + executionId)
						.build())
				.build();
	}

}

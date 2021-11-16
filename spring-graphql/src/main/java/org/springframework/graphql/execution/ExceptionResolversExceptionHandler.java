/*
 * Copyright 2002-2021 the original author or authors.
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
import graphql.schema.DataFetchingEnvironment;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link DataFetcherExceptionHandler} that invokes {@link DataFetcherExceptionResolver}'s
 * in a sequence until one returns a list of {@link GraphQLError}'s.
 *
 * @author Rossen Stoyanchev
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
		Throwable exception = getException(params);
		DataFetchingEnvironment environment = params.getDataFetchingEnvironment();
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Resolving exception", exception);
			}
			return Flux.fromIterable(this.resolvers)
					.flatMap((resolver) -> resolver.resolveException(exception, environment))
					.next()
					.map((errors) -> DataFetcherExceptionHandlerResult.newResult().errors(errors).build())
					.switchIfEmpty(Mono.fromCallable(() -> createInternalError(exception, environment)))
					.contextWrite((context) -> {
						ContextView contextView = ReactorContextManager.getReactorContext(environment);
						return (contextView.isEmpty() ? context : context.putAll(contextView));
					})
					.toFuture();
		}
		catch (Exception ex) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failed to handle " + exception.getMessage(), ex);
			}
			return CompletableFuture.completedFuture(createInternalError(exception, environment));
		}
	}

	private Throwable getException(DataFetcherExceptionHandlerParameters params) {
		Throwable ex = params.getException();
		return ((ex instanceof CompletionException) ? ex.getCause() : ex);
	}

	private DataFetcherExceptionHandlerResult createInternalError(Throwable ex, DataFetchingEnvironment env) {

		GraphQLError error = GraphqlErrorBuilder.newError(env)
				.errorType(ErrorType.INTERNAL_ERROR)
				.message((StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName()))
				.build();

		return DataFetcherExceptionHandlerResult.newResult(error).build();
	}

}

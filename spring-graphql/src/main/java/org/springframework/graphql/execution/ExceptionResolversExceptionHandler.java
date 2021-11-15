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
public class ExceptionResolversExceptionHandler implements DataFetcherExceptionHandler {

	private static final Log logger = LogFactory.getLog(ExceptionResolversExceptionHandler.class);

	private final List<DataFetcherExceptionResolver> resolvers;

	/**
	 * Create an instance.
	 * @param resolvers the resolvers to use
	 */
	public ExceptionResolversExceptionHandler(List<DataFetcherExceptionResolver> resolvers) {
		Assert.notNull(resolvers, "'resolvers' is required");
		this.resolvers = new ArrayList<>(resolvers);
	}

	@Override
	@Deprecated
	public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters parameters) {
		Throwable exception = parameters.getException();
		exception = ((exception instanceof CompletionException) ? exception.getCause() : exception);
		return invokeChain(exception, parameters.getDataFetchingEnvironment());
	}

	DataFetcherExceptionHandlerResult invokeChain(Throwable ex, DataFetchingEnvironment env) {
		// For now we have to block:
		// https://github.com/graphql-java/graphql-java/issues/2356
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Resolving exception", ex);
			}
			return Flux.fromIterable(this.resolvers)
					.flatMap((resolver) -> resolver.resolveException(ex, env))
					.next()
					.map((errors) -> DataFetcherExceptionHandlerResult.newResult().errors(errors).build())
					.switchIfEmpty(Mono.fromCallable(() -> applyDefaultHandling(ex, env)))
					.contextWrite((context) -> {
						ContextView contextView = ReactorContextManager.getReactorContext(env);
						return (contextView.isEmpty() ? context : context.putAll(contextView));
					})
					.toFuture()
					.get();
		}
		catch (Exception ex2) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failed to handle " + ex.getMessage(), ex2);
			}
			return applyDefaultHandling(ex, env);
		}
	}

	private DataFetcherExceptionHandlerResult applyDefaultHandling(Throwable ex, DataFetchingEnvironment env) {
		GraphqlErrorBuilder errorBuilder = GraphqlErrorBuilder.newError(env).errorType(ErrorType.INTERNAL_ERROR);
		errorBuilder.message(StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
		return DataFetcherExceptionHandlerResult.newResult(errorBuilder.build()).build();
	}

}

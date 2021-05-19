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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

import org.springframework.util.Assert;

/**
 * {@link DataFetcherExceptionHandler} that invokes {@link DataFetcherExceptionResolver}'s
 * in a sequence until one returns a non-null list of {@link GraphQLError}'s.
 */
class ExceptionResolversExceptionHandler implements DataFetcherExceptionHandler {

	private final List<DataFetcherExceptionResolver> resolvers;


	/**
	 * Create an instance
	 * @param resolvers the resolvers to use
	 */
	public ExceptionResolversExceptionHandler(List<DataFetcherExceptionResolver> resolvers) {
		Assert.notNull(resolvers, "'resolvers' is required");
		this.resolvers = new ArrayList<>(resolvers);
	}


	@Override
	public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters parameters) {
		Throwable exception = parameters.getException();
		exception = (exception instanceof CompletionException ? exception.getCause() : exception);
		return invokeChain(exception, parameters.getDataFetchingEnvironment());
	}

	@SuppressWarnings("ConstantConditions")
	public DataFetcherExceptionHandlerResult invokeChain(Throwable ex, DataFetchingEnvironment env) {
		return Flux.fromIterable(this.resolvers)
				.publishOn(Schedulers.boundedElastic())  // until GraphQL Java supports async exception handling
				.flatMap(resolver -> resolver.resolveException(ex, env))
				.next()
				.defaultIfEmpty(Collections.singletonList(applyDefaultHandling(ex, env)))
				.map(errors -> DataFetcherExceptionHandlerResult.newResult().errors(errors).build())
				.contextWrite(context -> {
					ContextView contextToAdd = ContextManager.getReactorContext(env);
					return (contextToAdd != null ? context.putAll(contextToAdd) : context);
				})
				.block();
	}

	private GraphQLError applyDefaultHandling(Throwable ex, DataFetchingEnvironment env) {
		return GraphqlErrorBuilder.newError(env)
				.message(ex.getMessage())
				.errorType(ErrorType.INTERNAL_ERROR)
				.build();
	}

}

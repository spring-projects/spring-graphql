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

import java.util.List;
import java.util.function.BiFunction;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;

/**
 * Contract to resolve exceptions from {@link graphql.schema.DataFetcher}s.
 * Implementations are typically declared as beans in Spring configuration and
 * are invoked sequentially until one emits a List of {@link GraphQLError}s.
 *
 * <p>Most resolver implementations can extend
 * {@link DataFetcherExceptionResolverAdapter} and override one of its
 * {@link DataFetcherExceptionResolverAdapter#resolveToSingleError resolveToSingleError} or
 * {@link DataFetcherExceptionResolverAdapter#resolveToMultipleErrors resolveToMultipleErrors}
 * methods that resolve the exception synchronously.
 *
 * <p>Resolver implementations can use {@link ErrorType} to classify errors
 * using one of several common categories.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see ErrorType
 * @see DataFetcherExceptionResolverAdapter
 * @see ExceptionResolversExceptionHandler
 */
public interface DataFetcherExceptionResolver {

	/**
	 * Resolve the given exception and return the error(s) to add to the response.
	 * <p>Implementations can use
	 * {@link graphql.GraphqlErrorBuilder#newError(DataFetchingEnvironment)} to
	 * create an error with the coordinates of the target field, and use
	 * {@link ErrorType} to specify a category for the error.
	 * @param exception the exception to resolve
	 * @param environment the environment for the invoked {@code DataFetcher}
	 * @return a {@code Mono} with errors to add to the GraphQL response;
	 * if the {@code Mono} completes with an empty List, the exception is resolved
	 * without any errors added to the response; if the {@code Mono} completes
	 * empty, without emitting a List, the exception remains unresolved and that
	 * allows other resolvers to resolve it.
	 */
	Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment);


	/**
	 * Factory method to create a {@link DataFetcherExceptionResolver} to resolve
	 * an exception to a single GraphQL error. Effectively, a shortcut
	 * for creating {@link DataFetcherExceptionResolverAdapter} and overriding
	 * its {@code resolveToSingleError} method.
	 * @param resolver the resolver function to use
	 * @return the created instance
	 * @since 1.0.1
	 */
	static DataFetcherExceptionResolverAdapter forSingleError(
			BiFunction<Throwable, DataFetchingEnvironment, GraphQLError> resolver) {

		return new DataFetcherExceptionResolverAdapter() {

			@Override
			protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
				return resolver.apply(ex, env);
			}
		};
	}

}

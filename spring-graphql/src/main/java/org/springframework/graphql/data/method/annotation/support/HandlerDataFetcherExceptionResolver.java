/*
 * Copyright 2002-2024 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import java.util.List;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.lang.Nullable;

/**
 * Extension of {@link DataFetcherExceptionResolver} with overloaded method to
 * apply at the point of DataFetcher invocation to allow local exception handling.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
public interface HandlerDataFetcherExceptionResolver extends DataFetcherExceptionResolver {


	@Override
	default Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment) {
		return resolveException(exception, environment, null);
	}

	/**
	 * Resolve an exception raised by the given handler.
	 * @param ex the exception to resolve
	 * @param environment the environment for the invoked {@code DataFetcher}
	 * @param handler the handler that raised the exception, if applicable
	 * @return a {@code Mono} with resolved {@code GraphQLError}s as specified in
	 * {@link DataFetcherExceptionResolver#resolveException(Throwable, DataFetchingEnvironment)}
	 */
	Mono<List<GraphQLError>> resolveException(
			Throwable ex, DataFetchingEnvironment environment, @Nullable Object handler);

}

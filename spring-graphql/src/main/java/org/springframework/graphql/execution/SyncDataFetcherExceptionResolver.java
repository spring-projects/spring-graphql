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

import java.util.List;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * {@link DataFetcherExceptionResolver} that resolves exceptions synchronously.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface SyncDataFetcherExceptionResolver extends DataFetcherExceptionResolver {

	@Override
	default Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment) {
		ContextView contextView = ContextManager.getReactorContext(environment);
		try {
			ContextManager.restoreThreadLocalValues(contextView);
			return Mono.just(doResolveException(exception, environment));
		}
		finally {
			ContextManager.resetThreadLocalValues(contextView);
		}
	}

	/**
	 * Implement this method to resolve exceptions.
	 * @param exception the exception to resolve
	 * @param environment the environment for the invoked {@code DataFetcher}
	 * @return the list of resolved GraphQL errors
	 */
	List<GraphQLError> doResolveException(Throwable exception, DataFetchingEnvironment environment);

}

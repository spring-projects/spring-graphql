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

import java.util.Collections;
import java.util.List;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;

/**
 * Simple adapter for {@link DataFetcherExceptionResolver} implementations that
 * resolve exceptions to a single error only.
 */
public abstract class SingleErrorExceptionResolver implements DataFetcherExceptionResolver {


	@Override
	public final Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment) {
		return doResolve(exception, environment).map(Collections::singletonList);
	}

	/**
	 * Implement this method to resolve the exception to an error.
	 */
	protected abstract Mono<GraphQLError> doResolve(Throwable exception, DataFetchingEnvironment environment);

}

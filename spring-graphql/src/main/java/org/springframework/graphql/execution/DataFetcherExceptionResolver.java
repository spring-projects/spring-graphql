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

import org.springframework.lang.Nullable;

/**
 * Contract to resolve exceptions raised by {@link graphql.schema.DataFetcher}'s
 * into errors to be added to the GraphQL response. Implementations are typically
 * declared as beans in Spring configuration and invoked in order until one
 * returns a non-null list of {@link GraphQLError}'s.
 */
public interface DataFetcherExceptionResolver {

	/**
	 * Resolve the given exception and return errors to add to the response.
	 * <p>Implementations can use
	 * {@link graphql.GraphqlErrorBuilder#newError(DataFetchingEnvironment)} to
	 * create an error with the coordinates of the target field, and use
	 * {@link ErrorType} to specify a category for the error.
	 * @param exception the exception to resolve
	 * @param environment the environment for the invoked {@code DataFetcher}
	 * @return a (possibly empty) list of {@link GraphQLError}'s to add to the
	 * response, or {@code null} to indicate the exception is unresolved.
	 */
	@Nullable
	List<GraphQLError> resolveException(Throwable exception, DataFetchingEnvironment environment);

}

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

package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;


/**
 * Implementation of {@link GraphQlResponse} that wraps the {@link ExecutionResult}
 * returned from {@link graphql.GraphQL} to expose it as {@link GraphQlResponse},
 * also providing access to the {@link ExecutionInput} used for the request.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface ExecutionGraphQlResponse extends GraphQlResponse {

	/**
	 * Return the {@link ExecutionInput} that was prepared through the
	 * {@link ExecutionGraphQlRequest} and passed to {@link graphql.GraphQL}.
	 */
	ExecutionInput getExecutionInput();

	/**
	 * Return the {@link ExecutionResult} that was returned from the invocation
	 * to {@link graphql.GraphQL}.
	 */
	ExecutionResult getExecutionResult();

}

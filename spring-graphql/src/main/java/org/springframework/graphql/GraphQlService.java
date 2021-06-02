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

package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import reactor.core.publisher.Mono;

/**
 * Strategy to perform GraphQL request execution with input for and output from the
 * invocation of {@link graphql.GraphQL}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlService {

	/**
	 * Perform the operation and return the result.
	 * @param input the input for the {@link graphql.GraphQL} invocation
	 * @return the execution result
	 */
	Mono<ExecutionResult> execute(ExecutionInput input);

}

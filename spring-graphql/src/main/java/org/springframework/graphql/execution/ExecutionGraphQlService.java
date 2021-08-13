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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.RequestInput;

/**
 * {@link GraphQlService} that uses a {@link GraphQlSource} to obtain a
 * {@link GraphQL} instance and perform query execution.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ExecutionGraphQlService implements GraphQlService {

	private final GraphQlSource graphQlSource;

	public ExecutionGraphQlService(GraphQlSource graphQlSource) {
		this.graphQlSource = graphQlSource;
	}

	@Override
	public final Mono<ExecutionResult> execute(RequestInput requestInput) {
		ExecutionInput executionInput = requestInput.toExecutionInput();

		GraphQL graphQl = this.graphQlSource.graphQl();

		return Mono.deferContextual((contextView) -> {
			ReactorContextManager.setReactorContext(contextView, executionInput);
			return Mono.fromFuture(graphQl.executeAsync(executionInput));
		});
	}

}

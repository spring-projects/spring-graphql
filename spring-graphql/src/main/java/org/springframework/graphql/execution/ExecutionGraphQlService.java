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

/**
 * Implementation of {@link GraphQlService} that performs GraphQL request execution
 * through {@link GraphQL#executeAsync(ExecutionInput)}.
 */
public class ExecutionGraphQlService implements GraphQlService {

	private final GraphQlSource graphQlSource;


	public ExecutionGraphQlService(GraphQlSource graphQlSource) {
		this.graphQlSource = graphQlSource;
	}


	@Override
	public Mono<ExecutionResult> execute(ExecutionInput input) {
		GraphQL graphQl = this.graphQlSource.graphQl();
		return Mono.deferContextual(contextView -> {
			ContextManager.setReactorContext(contextView, input);
			return Mono.fromFuture(graphQl.executeAsync(input));
		});
	}

}

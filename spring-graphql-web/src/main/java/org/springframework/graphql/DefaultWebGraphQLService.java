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

import java.util.concurrent.CompletableFuture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;

/**
 * Extension of {@link AbstractWebGraphQLService} that executes GraphQL queries
 * through the {@link GraphQL} instance it is configured with.
 */
public class DefaultWebGraphQLService extends AbstractWebGraphQLService {

	private final GraphQL graphQL;


	public DefaultWebGraphQLService(GraphQL graphQL) {
		this.graphQL = graphQL;
	}


	@Override
	protected CompletableFuture<ExecutionResult> executeInternal(ExecutionInput input) {
		return this.graphQL.executeAsync(input);
	}

}

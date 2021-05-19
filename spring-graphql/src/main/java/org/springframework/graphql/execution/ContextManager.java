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
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import reactor.util.context.ContextView;

import org.springframework.lang.Nullable;

/**
 * Package private utility class for propagating a Reactor {@link ContextView}
 * through the {@link ExecutionInput} and the {@link DataFetchingEnvironment}
 * of a request.
 */
abstract class ContextManager {

	private static final String REACTOR_CONTEXT_KEY =
			ReactorDataFetcherAdapter.class.getName() + ".REACTOR_CONTEXT";


	/**
	 * Save the given Reactor ContextView in the an {@link ExecutionInput} for
	 * later access through the {@link DataFetchingEnvironment}.
	 */
	static void setReactorContext(ContextView contextView, ExecutionInput input) {
		((GraphQLContext) input.getContext()).put(REACTOR_CONTEXT_KEY, contextView);
	}

	/**
	 * Return the Reactor ContextView saved in the given DataFetchingEnvironment,
	 * or null if not present.
	 */
	@Nullable
	static ContextView getReactorContext(DataFetchingEnvironment environment) {
		GraphQLContext graphQlContext = environment.getContext();
		return graphQlContext.get(REACTOR_CONTEXT_KEY);
	}

}

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

import java.util.LinkedHashMap;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.lang.Nullable;

/**
 * Package private utility class for propagating a Reactor {@link ContextView} through the
 * {@link ExecutionInput} and the {@link DataFetchingEnvironment} of a request.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public abstract class ContextManager {

	private static final String CONTEXT_VIEW_KEY = ContextManager.class.getName() + ".CONTEXT_VIEW";

	private static final String THREAD_LOCAL_VALUES_KEY = ContextManager.class.getName() + ".THREAD_VALUES_ACCESSOR";

	private static final String THREAD_LOCAL_ACCESSOR_KEY = ContextManager.class.getName() + ".THREAD_LOCAL_ACCESSOR";

	/**
	 * Save the given Reactor {@link ContextView} in the an {@link ExecutionInput} for
	 * later access through the {@link DataFetchingEnvironment}.
	 * @param contextView the reactor context view
	 * @param input the GraphQL query input
	 */
	static void setReactorContext(ContextView contextView, ExecutionInput input) {
		((GraphQLContext) input.getContext()).put(CONTEXT_VIEW_KEY, contextView);
	}

	/**
	 * Return the Reactor {@link ContextView} saved in the given DataFetchingEnvironment.
	 * @param environment the DataFetchingEnvironment
	 * @return the reactor {@link ContextView}
	 */
	static ContextView getReactorContext(DataFetchingEnvironment environment) {
		GraphQLContext graphQlContext = environment.getContext();
		return graphQlContext.getOrDefault(CONTEXT_VIEW_KEY, Context.empty());
	}

	/**
	 * Use the given accessor to extract ThreadLocal value, and return a Reactor context
	 * that contains both the extracted values and the accessor.
	 * @param accessor the accessor to use
	 * @return the reactor {@link ContextView}
	 */
	public static ContextView extractThreadLocalValues(ThreadLocalAccessor accessor) {
		Map<String, Object> valuesMap = new LinkedHashMap<>();
		accessor.extractValues(valuesMap);
		return Context.of(THREAD_LOCAL_VALUES_KEY, valuesMap, THREAD_LOCAL_ACCESSOR_KEY, accessor);
	}

	/**
	 * Look up saved ThreadLocal values and use them to re-establish ThreadLocal context.
	 * @param contextView the reactor {@link ContextView}
	 */
	static void restoreThreadLocalValues(ContextView contextView) {
		ThreadLocalAccessor accessor = getThreadLocalAccessor(contextView);
		if (accessor != null) {
			accessor.restoreValues(getThreadLocalValues(contextView));
		}
	}

	/**
	 * Look up saved ThreadLocal values and remove associated ThreadLocal context.
	 * @param contextView the reactor {@link ContextView}
	 */
	static void resetThreadLocalValues(ContextView contextView) {
		ThreadLocalAccessor accessor = getThreadLocalAccessor(contextView);
		if (accessor != null) {
			accessor.resetValues(getThreadLocalValues(contextView));
		}
	}

	@Nullable
	private static ThreadLocalAccessor getThreadLocalAccessor(ContextView contextView) {
		return (contextView.hasKey(THREAD_LOCAL_ACCESSOR_KEY) ? contextView.get(THREAD_LOCAL_ACCESSOR_KEY) : null);
	}

	private static Map<String, Object> getThreadLocalValues(ContextView contextView) {
		return contextView.get(THREAD_LOCAL_VALUES_KEY);
	}

}

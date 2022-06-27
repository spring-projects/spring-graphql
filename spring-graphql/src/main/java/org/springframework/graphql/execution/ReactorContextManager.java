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

package org.springframework.graphql.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import graphql.GraphQLContext;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Provides helper methods to save Reactor context in the {@link GraphQLContext}
 * so it can be subsequently obtained and propagated to data fetchers, exception
 * handlers, and others.
 *
 * <p>The Reactor context is also used to carry ThreadLocal values that are also
 * restored around the execution of data fetchers and exceptions handlers.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public abstract class ReactorContextManager {

	private static final String CONTEXT_VIEW_KEY = ReactorContextManager.class.getName() + ".CONTEXT_VIEW";

	private static final String THREAD_ID = ReactorContextManager.class.getName() + ".THREAD_ID";

	private static final String THREAD_LOCAL_VALUES_KEY = ReactorContextManager.class.getName() + ".THREAD_VALUES_ACCESSOR";

	private static final String THREAD_LOCAL_ACCESSOR_KEY = ReactorContextManager.class.getName() + ".THREAD_LOCAL_ACCESSOR";

	/**
	 * Save the given Reactor {@link ContextView} in the given {@link GraphQLContext}.
	 * @param contextView the reactor {@code ContextView} to save
	 * @param graphQLContext the {@code GraphQLContext} where to save
	 */
	static void setReactorContext(ContextView contextView, GraphQLContext graphQLContext) {
		graphQLContext.put(CONTEXT_VIEW_KEY, contextView);
	}

	/**
	 * Return the Reactor {@link ContextView} saved in the given {@link GraphQLContext}.
	 * @param graphQlContext the DataFetchingEnvironment
	 * @return the reactor {@link ContextView}
	 */
	static ContextView getReactorContext(GraphQLContext graphQlContext) {
		Assert.notNull(graphQlContext, "GraphQLContext is required");
		return graphQlContext.getOrDefault(CONTEXT_VIEW_KEY, Context.empty());
	}

	/**
	 * Use the given accessor to extract ThreadLocal values and save them in a
	 * sub-map in the given {@link Context}, so those can be restored later
	 * around the execution of data fetchers and exception resolvers. The accessor
	 * instance is also saved in the Reactor Context, so it can be used to
	 * actually restore and reset ThreadLocal values.
	 * @param accessor the accessor to use
	 * @param context the context to write to if there are ThreadLocal values
	 * @return a new Reactor {@link ContextView} or the {@code Context} instance
	 * that was passed in, if there were no ThreadLocal values to extract.
	 */
	public static Context extractThreadLocalValues(ThreadLocalAccessor accessor, Context context) {
		Map<String, Object> valuesMap = new LinkedHashMap<>();
		accessor.extractValues(valuesMap);
		if (valuesMap.isEmpty()) {
			return context;
		}
		return context.putAll((ContextView) Context.of(
				THREAD_LOCAL_VALUES_KEY, valuesMap,
				THREAD_LOCAL_ACCESSOR_KEY, accessor,
				THREAD_ID, Thread.currentThread().getId()));
	}

	/**
	 * Restore {@code ThreadLocal} values, invoke the given {@code Callable},
	 * and reset the {@code ThreadLocal} values.
	 * @param callable the callable to invoke
	 * @param graphQlContext the current {@code GraphQLContext}
	 * @return the return value from the invocation
	 */
	public static <T> T invokeCallable(Callable<T> callable, GraphQLContext graphQlContext) throws Exception {
		ContextView contextView = getReactorContext(graphQlContext);
		try {
			ReactorContextManager.restoreThreadLocalValues(contextView);
			return callable.call();
		}
		finally {
			ReactorContextManager.resetThreadLocalValues(contextView);
		}
	}

	/**
	 * Look up saved ThreadLocal values and restore them if any are found.
	 * This is a no-op if invoked on the thread that values were extracted on.
	 * @param contextView the reactor {@link ContextView}
	 */
	static void restoreThreadLocalValues(ContextView contextView) {
		ThreadLocalAccessor accessor = getThreadLocalAccessor(contextView);
		if (accessor != null) {
			accessor.restoreValues(contextView.get(THREAD_LOCAL_VALUES_KEY));
		}
	}

	/**
	 * Look up saved ThreadLocal values and remove the ThreadLocal values.
	 * This is a no-op if invoked on the thread that values were extracted on.
	 * @param contextView the reactor {@link ContextView}
	 */
	static void resetThreadLocalValues(ContextView contextView) {
		ThreadLocalAccessor accessor = getThreadLocalAccessor(contextView);
		if (accessor != null) {
			accessor.resetValues(contextView.get(THREAD_LOCAL_VALUES_KEY));
		}
	}

	@Nullable
	private static ThreadLocalAccessor getThreadLocalAccessor(ContextView view) {
		Long id = view.getOrDefault(THREAD_ID, null);
		return (id != null && id != Thread.currentThread().getId() ? view.get(THREAD_LOCAL_ACCESSOR_KEY) : null);
	}

}

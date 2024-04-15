/*
 * Copyright 2002-2024 the original author or authors.
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

import graphql.GraphQLContext;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.lang.Nullable;

/**
 * Helper to use a single {@link ContextSnapshotFactory} instance by saving and
 * obtaining it to and from Reactor and GraphQL contexts.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public abstract class ContextSnapshotFactoryHelper {

	private static final ContextSnapshotFactory sharedInstance = ContextSnapshotFactory.builder().build();

	private static final String CONTEXT_SNAPSHOT_FACTORY_KEY = ContextSnapshotFactoryHelper.class.getName() + ".KEY";


	/**
	 * Select a {@code ContextSnapshotFactory} instance to use, either the one
	 * passed in if it is not {@code null}, or a shared, static instance.
	 * @param factory the candidate factory instance to use if not {@code null}
	 * @return the instance to use
	 */
	public static ContextSnapshotFactory selectInstance(@Nullable ContextSnapshotFactory factory) {
		if (factory != null) {
			return factory;
		}
		return sharedInstance;
	}

	/**
	 * Save the {@code ContextSnapshotFactory} in the given {@link Context}.
	 * @param factory the instance to save
	 * @param context the context to save the instance to
	 * @return a new context with the saved instance
	 */
	public static Context saveInstance(ContextSnapshotFactory factory, Context context) {
		return context.put(CONTEXT_SNAPSHOT_FACTORY_KEY, factory);
	}

	/**
	 * Save the {@code ContextSnapshotFactory} in the given {@link Context}.
	 * @param factory the instance to save
	 * @param context the context to save the instance to
	 */
	public static void saveInstance(ContextSnapshotFactory factory, GraphQLContext context) {
		context.put(CONTEXT_SNAPSHOT_FACTORY_KEY, factory);
	}

	/**
	 * Access the {@code ContextSnapshotFactory} from the given {@link ContextView}
	 * or return a shared, static instance.
	 * @param contextView the context where the instance is saved
	 * @return the instance to use
	 */
	public static ContextSnapshotFactory getInstance(ContextView contextView) {
		ContextSnapshotFactory factory = contextView.getOrDefault(CONTEXT_SNAPSHOT_FACTORY_KEY, null);
		return selectInstance(factory);
	}

	/**
	 * Access the {@code ContextSnapshotFactory} from the given {@link GraphQLContext}
	 * or return a shared, static instance.
	 * @param context the context where the instance is saved
	 * @return the instance to use
	 */
	public static ContextSnapshotFactory getInstance(GraphQLContext context) {
		ContextSnapshotFactory factory = context.get(CONTEXT_SNAPSHOT_FACTORY_KEY);
		return selectInstance(factory);
	}

	/**
	 * Shortcut to obtain the {@code ContextSnapshotFactory} instance, and to
	 * capture from the given {@link ContextView}.
	 * @param contextView the context to capture from
	 * @return a snapshot from the capture
	 */
	public static ContextSnapshot captureFrom(ContextView contextView) {
		ContextSnapshotFactory factory = getInstance(contextView);
		return selectInstance(factory).captureFrom(contextView);
	}

	/**
	 * Shortcut to obtain the {@code ContextSnapshotFactory} instance, and to
	 * capture from the given {@link GraphQLContext}.
	 * @param context the context to capture from
	 * @return a snapshot from the capture
	 */
	public static ContextSnapshot captureFrom(GraphQLContext context) {
		ContextSnapshotFactory factory = getInstance(context);
		return selectInstance(factory).captureFrom(context);
	}

}

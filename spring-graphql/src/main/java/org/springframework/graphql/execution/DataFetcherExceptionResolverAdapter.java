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

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.lang.Nullable;

/**
 * Adapter for {@link DataFetcherExceptionResolver} that pre-implements the
 * asynchronous contract and exposes the following synchronous protected methods:
 * <ul>
 * <li>{@link #resolveToSingleError}
 * <li>{@link #resolveToMultipleErrors}
 * </ul>
 *
 * <p>Applications may also use
 * {@link DataFetcherExceptionResolver#forSingleError(BiFunction)} as a shortcut
 * for {@link #resolveToSingleError(Throwable, DataFetchingEnvironment)}.
 *
 * <p>Implementations can also express interest in ThreadLocal context
 * propagation, from the underlying transport thread, via
 * {@link #setThreadLocalContextAware(boolean)}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public abstract class DataFetcherExceptionResolverAdapter implements DataFetcherExceptionResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	private boolean threadLocalContextAware;


	/**
	 * Protected constructor since this class is meant to be extended to provide
	 * the actual exception resolution logic.
	 */
	protected DataFetcherExceptionResolverAdapter() {
	}


	/**
	 * Subclasses can set this to indicate that ThreadLocal context from the
	 * transport handler (e.g. HTTP handler) should be restored when resolving
	 * exceptions.
	 * <p><strong>Note:</strong> This property is applicable only if transports
	 * use ThreadLocal's' (e.g. Spring MVC) and if a {@link ThreadLocalAccessor}
	 * is registered to extract ThreadLocal values of interest. There is no
	 * impact from setting this property otherwise.
	 * <p>By default this is set to "false" in which case there is no attempt
	 * to propagate ThreadLocal context.
	 * @param threadLocalContextAware whether this resolver needs access to
	 * ThreadLocal context or not.
	 */
	public void setThreadLocalContextAware(boolean threadLocalContextAware) {
		this.threadLocalContextAware = threadLocalContextAware;
	}

	/**
	 * Whether ThreadLocal context needs to be restored for this resolver.
	 */
	public boolean isThreadLocalContextAware() {
		return this.threadLocalContextAware;
	}

	@Override
	public final Mono<List<GraphQLError>> resolveException(Throwable ex, DataFetchingEnvironment env) {
		return Mono.defer(() -> Mono.justOrEmpty(resolveInternal(ex, env)));
	}

	@Nullable
	private List<GraphQLError> resolveInternal(Throwable exception, DataFetchingEnvironment env) {
		if (!this.threadLocalContextAware) {
			return resolveToMultipleErrors(exception, env);
		}
		try {
			return ContextSnapshot.captureFrom(env.getGraphQlContext())
					.wrap(() -> resolveToMultipleErrors(exception, env))
					.call();
		}
		catch (Exception ex2) {
			logger.warn("Failed to resolve " + exception, ex2);
			return null;
		}
	}

	/**
	 * Override this method to resolve an Exception to multiple GraphQL errors.
	 * @param ex the exception to resolve
	 * @param env the environment for the invoked {@code DataFetcher}
	 * @return the resolved errors or {@code null} if unresolved
	 */
	@Nullable
	protected List<GraphQLError> resolveToMultipleErrors(Throwable ex, DataFetchingEnvironment env) {
		GraphQLError error = resolveToSingleError(ex, env);
		return (error != null ? Collections.singletonList(error) : null);
	}

	/**
	 * Override this method to resolve an Exception to a single GraphQL error.
	 * @param ex the exception to resolve
	 * @param env the environment for the invoked {@code DataFetcher}
	 * @return the resolved error or {@code null} if unresolved
	 */
	@Nullable
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		return null;
	}


	/**
	 * Factory method to create a {@link DataFetcherExceptionResolverAdapter} that
	 * resolves exceptions with the given {@code BiFunction}.
	 * @param resolver the resolver function to use
	 * @return the created instance
	 * @deprecated as of 1.0.1, please use {@link DataFetcherExceptionResolver#forSingleError(BiFunction)}
	 */
	@Deprecated
	public static DataFetcherExceptionResolverAdapter from(
			BiFunction<Throwable, DataFetchingEnvironment, GraphQLError> resolver) {

		return new DataFetcherExceptionResolverAdapter() {

			@Override
			protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
				return resolver.apply(ex, env);
			}
		};
	}

}

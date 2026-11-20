/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import graphql.GraphQLError;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

/**
 * Adapter for {@link SubscriptionExceptionResolver} that pre-implements the
 * asynchronous contract and exposes the following synchronous protected methods:
 * <ul>
 * <li>{@link #resolveToSingleError}
 * <li>{@link #resolveToMultipleErrors}
 * </ul>
 *
 * <p>Applications may also use
 * {@link SubscriptionExceptionResolver#forSingleError(Function)} as a shortcut
 * for {@link #resolveToSingleError(Throwable)}.
 *
 * @author Mykyta Ivchenko
 * @author Rossen Stoyanchev
 * @since 1.0.1
 * @see SubscriptionExceptionResolver
 */
public abstract class SubscriptionExceptionResolverAdapter implements SubscriptionExceptionResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	private boolean threadLocalContextAware;


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


	@SuppressWarnings({"unused", "try"})
	@Override
	public final Mono<List<GraphQLError>> resolveException(Throwable exception) {
		if (this.threadLocalContextAware) {
			return Mono.deferContextual((contextView) -> {
				ContextSnapshot snapshot = ContextPropagationHelper.captureFrom(contextView);
				try {
					List<GraphQLError> errors = snapshot.wrap(() -> resolveToMultipleErrors(exception)).call();
					return Mono.justOrEmpty(errors);
				}
				catch (Exception ex2) {
					this.logger.warn("Failed to resolve " + exception, ex2);
					return Mono.empty();
				}
			});
		}
		else {
			return Mono.justOrEmpty(resolveToMultipleErrors(exception));
		}
	}

	/**
	 * Override this method to resolve the Exception to multiple GraphQL errors.
	 * @param exception the exception to resolve
	 * @return the resolved errors or {@code null} if unresolved
	 */
	protected @Nullable List<GraphQLError> resolveToMultipleErrors(Throwable exception) {
		GraphQLError error = resolveToSingleError(exception);
		return (error != null) ? Collections.singletonList(error) : null;
	}

	/**
	 * Override this method to resolve the Exception to a single GraphQL error.
	 * @param exception the exception to resolve
	 * @return the resolved error or {@code null} if unresolved
	 */
	protected @Nullable GraphQLError resolveToSingleError(Throwable exception) {
		return null;
	}

}

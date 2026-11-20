/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;

/**
 * Strategy interface for resolving method parameters into argument values in
 * the context of a given {@link DataFetchingEnvironment}.
 *
 * <p>Most implementations will be synchronous, simply resolving values from the
 * {@code DataFetchingEnvironment}. However, a resolver may also return a
 * {@link reactor.core.publisher.Mono} if it needs to be asynchronous.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface HandlerMethodArgumentResolver {

	/**
	 * Whether this resolver supports the given {@link MethodParameter}.
	 * @param parameter the method parameter to check for support
	 */
	boolean supportsParameter(MethodParameter parameter);

	/**
	 * Resolve a method parameter to a value.
	 * @param parameter the method parameter to resolve. This parameter must
	 * have previously checked via {@link #supportsParameter}.
	 * @param environment the environment to use to resolve the value
	 * @return the resolved value, which may be {@code null} if not resolved;
	 * the value may also be a {@link reactor.core.publisher.Mono} if it
	 * requires asynchronous resolution.
	 * @throws Exception in case of errors with the preparation of argument values
	 */
	@Nullable Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception;

}

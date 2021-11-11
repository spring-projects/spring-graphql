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
package org.springframework.graphql.data.method;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;

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
	 */
	boolean supportsParameter(MethodParameter parameter);

	/**
	 * Resolve a method parameter to a value.
	 *
	 * @param parameter the method parameter to resolve. This parameter must
	 * have previously checked via {@link #supportsParameter}.
	 * @param environment the environment to use to resolve the value
	 *
	 * @return the resolved value, which may be {@code null} if not resolved;
	 * the value may also be a {@link reactor.core.publisher.Mono} if it
	 * requires asynchronous resolution.
	 *
	 * @throws Exception in case of errors with the preparation of argument values
	 */
	@Nullable
	Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception;

}

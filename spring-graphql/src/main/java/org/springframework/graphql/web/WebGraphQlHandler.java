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

package org.springframework.graphql.web;

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.execution.ThreadLocalAccessor;

/**
 * Common contract to handle a GraphQL request over HTTP or WebSocket for use
 * with both Spring MVC and Spring WebFlux.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebGraphQlHandler {

	/**
	 * Perform request execution for the given input and return the result.
	 * @param input the GraphQL request input container
	 * @return the execution result
	 */
	Mono<WebOutput> handle(WebInput input);

	/**
	 * Provides access to a builder to create a {@link WebGraphQlHandler} instance.
	 * @param graphQlService the {@link GraphQlService} to use for actual execution of the
	 * request.
	 * @return a builder for a WebGraphQlHandler
	 */
	static Builder builder(GraphQlService graphQlService) {
		return new DefaultWebGraphQlHandlerBuilder(graphQlService);
	}

	/**
	 * Builder for a {@link WebGraphQlHandler} that executes a
	 * {@link WebInterceptor} chain followed by a {@link GraphQlService}.
	 */
	interface Builder {

		/**
		 * Configure interceptors to be invoked before the target {@code GraphQlService}.
		 * @param interceptors the interceptors to add
		 * @return this builder
		 */
		Builder interceptor(WebInterceptor... interceptors);

		/**
		 * Alternative to {@link #interceptor(WebInterceptor...)} with a List.
		 * @param interceptors the list of interceptors to add
		 * @return this builder
		 */
		Builder interceptors(List<WebInterceptor> interceptors);

		/**
		 * Configure accessors for ThreadLocal variables to use to extract
		 * ThreadLocal values at the start of GraphQL execution in the web layer,
		 * and have those saved, and restored around the invocation of data
		 * fetchers and exception resolvers.
		 * @param accessors the accessors to add
		 * @return this builder
		 */
		Builder threadLocalAccessor(ThreadLocalAccessor... accessors);

		/**
		 * Alternative to {@link #threadLocalAccessor(ThreadLocalAccessor...)} with a
		 * List.
		 * @param accessors the list of accessors to add
		 * @return this builder
		 */
		Builder threadLocalAccessors(List<ThreadLocalAccessor> accessors);

		/**
		 * Build the {@link WebGraphQlHandler} instance.
		 * @return the built WebGraphQlHandler
		 */
		WebGraphQlHandler build();

	}

}

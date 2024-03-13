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

package org.springframework.graphql.server;

import java.util.List;

import io.micrometer.context.ContextSnapshotFactory;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;


/**
 * Contract for common handling of a GraphQL request over HTTP or WebSocket,
 * for use with Spring MVC or Spring WebFlux.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebGraphQlHandler {

	/**
	 * Return the single interceptor of type
	 * {@link WebSocketGraphQlInterceptor} among all the configured
	 * interceptors.
	 */
	WebSocketGraphQlInterceptor getWebSocketInterceptor();

	/**
	 * Return the {@link WebGraphQlHandler.Builder#contextSnapshotFactory configured}
	 * {@code ContextSnapshotFactory} instance to use.
	 * @since 1.3
	 */
	ContextSnapshotFactory contextSnapshotFactory();

	/**
	 * Execute the given request and return the response.
	 * @param request the request to execute
	 * @return the response
	 */
	Mono<WebGraphQlResponse> handleRequest(WebGraphQlRequest request);


	/**
	 * Provides access to a builder to create a {@link WebGraphQlHandler} instance.
	 * @param graphQlService the {@link ExecutionGraphQlService} to use for actual execution of the
	 * request.
	 * @return a builder for a WebGraphQlHandler
	 */
	static Builder builder(ExecutionGraphQlService graphQlService) {
		return new DefaultWebGraphQlHandlerBuilder(graphQlService);
	}


	/**
	 * Builder for a {@link WebGraphQlHandler} that executes a
	 * {@link WebGraphQlInterceptor} chain followed by a
	 * {@link ExecutionGraphQlService}.
	 */
	interface Builder {

		/**
		 * Configure interceptors to be invoked before the target
		 * {@code GraphQlService}.
		 * <p>One of the interceptors can be of type
		 * {@link WebSocketGraphQlInterceptor} to handle data from the
		 * first {@code ConnectionInit} message expected on a GraphQL over
		 * WebSocket session, as well as the {@code Complete} message expected
		 * at the end of a session.
		 * @param interceptors the interceptors to add
		 * @return this builder
		 */
		Builder interceptor(WebGraphQlInterceptor... interceptors);

		/**
		 * Alternative to {@link #interceptor(WebGraphQlInterceptor...)}
		 * with a List.
		 * @param interceptors the list of interceptors to add
		 * @return this builder
		 */
		Builder interceptors(List<WebGraphQlInterceptor> interceptors);

		/**
		 * Configure the {@link ContextSnapshotFactory} instance to use for
		 * context propagation of {@code ThreadLocal}, and Reactor context
		 * values from the transport layer to {@link DefaultExecutionGraphQlService}.
		 * <p>Note that there are other components that would also need to be
		 * configured similarly to use a single instance:
		 * <ul>
		 * <li>{@link DefaultExecutionGraphQlService#setContextSnapshotFactory}
		 * <li>{@link DefaultBatchLoaderRegistry#setContextSnapshotFactory}
		 * <li>{@link GraphQlSource.Builder#contextSnapshotFactory}
		 * </ul>
		 * If not set, then an instance with default settings is used.
		 * @since 1.3
		 */
		Builder contextSnapshotFactory(ContextSnapshotFactory snapshotFactory);

		/**
		 * Build the {@link WebGraphQlHandler} instance.
		 * @return the built WebGraphQlHandler
		 */
		WebGraphQlHandler build();

	}

}

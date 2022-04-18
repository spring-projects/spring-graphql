/*
 * Copyright 2020-2022 the original author or authors.
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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.util.Assert;


/**
 * Interceptor for server handling of GraphQL over HTTP or WebSocket requests,
 * providing access to info about the underlying HTTP request or WebSocket
 * handshake, and allowing customization of the {@link ExecutionInput} and
 * the {@link ExecutionResult}.
 *
 * <p>Interceptors are typically declared as beans in Spring configuration and
 * ordered as defined in {@link ObjectProvider#orderedStream()}.
 *
 * <p>Supported for Spring MVC and WebFlux.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see WebSocketGraphQlInterceptor
 */
public interface WebGraphQlInterceptor {

	/**
	 * Intercept a request and delegate to the rest of the chain including other
	 * interceptors and a {@link ExecutionGraphQlService}.
	 * @param request the request which may be a {@link WebSocketGraphQlRequest}
	 * when intercepting a GraphQL request over WebSocket
	 * @param chain the rest of the chain to execute the request
	 * @return a {@link Mono} with the response
	 */
	Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain);

	/**
	 * Return a new {@link WebGraphQlInterceptor} that invokes the current
	 * interceptor first and then the one that is passed in.
	 * @param nextInterceptor the interceptor to delegate to after the current
	 * @return a new interceptor that chains the two
	 */
	default WebGraphQlInterceptor andThen(WebGraphQlInterceptor nextInterceptor) {
		return (request, chain) -> intercept(request, nextRequest -> {
			if (request instanceof WebSocketGraphQlRequest) {
				Assert.isTrue(nextRequest instanceof WebSocketGraphQlRequest,
						"Expected WebSocketGraphQlRequest but was: " + nextRequest.getClass().getName());
			}
			return nextInterceptor.intercept(nextRequest, chain);
		});
	}

	/**
	 * Apply this interceptor to the given {@code Chain} resulting in an intercepted chain.
	 * @param chain the chain to add interception around
	 * @return a new chain instance
	 */
	default Chain apply(Chain chain) {
		return request -> intercept(request, chain);
	}


	/**
	 * Contract for delegation to the rest of the chain.
	 */
	interface Chain {

		/**
		 * Delegate to the rest of the chain to execute the request.
		 * @param request the request to execute
		 * the {@link ExecutionInput} for {@link graphql.GraphQL}.
		 * @return {@code Mono} with the response
		 */
		Mono<WebGraphQlResponse> next(WebGraphQlRequest request);

	}

}

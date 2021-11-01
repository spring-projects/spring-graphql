/*
 * Copyright 2020-2021 the original author or authors.
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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.Assert;

/**
 * Interceptor for intercepting GraphQL over HTTP or GraphQL over WebSocket
 * requests. Provides information about the HTTP request or WebSocket handshake,
 * and allows customization of the {@link ExecutionInput} as well as of the
 * {@link ExecutionResult}.
 *
 * <p> Interceptors are typically declared as beans in Spring configuration and
 * ordered as defined in {@link ObjectProvider#orderedStream()}.
 *
 * <p> Supported for Spring MVC and WebFlux.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see WebSocketInterceptor
 */
public interface WebInterceptor {

	/**
	 * Intercept a request and possibly delegate to the rest of the chain
	 * consisting of more interceptors as well as a
	 * {@link org.springframework.graphql.GraphQlService} at the end to actually
	 * handle the request through the GraphQL engine.
	 * @param webInput container for HTTP request information and options to
	 * customize the {@link ExecutionInput}.
	 * @param chain the rest of the chain to delegate to for request execution
	 * @return a {@link Mono} with the result
	 */
	Mono<WebOutput> intercept(WebInput webInput, WebInterceptorChain chain);

	/**
	 * Return a composed {@link WebInterceptor} that invokes the current
	 * interceptor first and then the one that is passed in.
	 * @param interceptor the interceptor to delegate to after "this" interceptor
	 * @return the composed WebInterceptor
	 */
	default WebInterceptor andThen(WebInterceptor interceptor) {
		Assert.notNull(interceptor, "WebInterceptor must not be null");
		return (currentInput, next) -> intercept(currentInput, (nextInput) -> interceptor.intercept(nextInput, next));
	}

}

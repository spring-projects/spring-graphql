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

package org.springframework.graphql.web;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.Assert;

/**
 * Interceptor for the handling of GraphQL over HTTP or GraphQL over WebSocket
 * requests. Exposes the details of the underlying HTTP request or WebSocket
 * handshake, the decoded GraphQL request, and allows customization of the
 * {@link ExecutionInput} and the resulting {@link ExecutionResult}.
 *
 * <p>Interceptors are typically declared as beans in Spring configuration and
 * ordered as defined in {@link ObjectProvider#orderedStream()}.
 *
 * <p>Supported for Spring MVC and WebFlux.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see WebSocketInterceptor
 */
public interface WebInterceptor {

	/**
	 * Intercept a request and delegate to the rest of the chain that consists
	 * of other interceptors followed by a
	 * {@link org.springframework.graphql.GraphQlService} that executes the
	 * request through the GraphQL Java.
	 * @param webInput provides access to GraphQL request input and allows
	 * customizing the {@link ExecutionInput} that will be used.
	 * @param chain the rest of the chain to handle the request
	 * @return a {@link Mono} with the result
	 */
	Mono<WebOutput> intercept(WebInput webInput, WebInterceptorChain chain);

	/**
	 * Return a new {@link WebInterceptor} that invokes the current interceptor
	 * first and then the one that is passed in.
	 * @param interceptor the interceptor to delegate to after "this"
	 * @return the new {@code WebInterceptor}
	 */
	default WebInterceptor andThen(WebInterceptor interceptor) {
		Assert.notNull(interceptor, "WebInterceptor is required");
		return (currentInput, next) -> intercept(currentInput,
				(nextInput) -> interceptor.intercept(nextInput, next));
	}

}

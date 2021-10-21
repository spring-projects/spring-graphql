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

import java.util.Map;

import reactor.core.publisher.Mono;

/**
 * An extension of {@link WebInterceptor} with additional methods to handle the
 * start and end of a WebSocket connection. Only a single interceptor of type
 * {@link WebSocketInterceptor} may be declared.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebSocketInterceptor extends WebInterceptor {

	@Override
	default Mono<WebOutput> intercept(WebInput webInput, WebInterceptorChain chain) {
		return chain.next(webInput);
	}

	/**
	 * Handle the payload from the connection initialization message that a
	 * GraphQL over WebSocket client must send after the WebSocket session is
	 * established and before sending any requests.
	 * @param payload the payload from the {@code ConnectionInit} message
	 * @return an optional payload for the {@code ConnectionAck} message
	 */
	default Mono<Object> handleConnectionInitialization(Map<String, Object> payload) {
		return Mono.empty();
	}

	/**
	 * Handle the completion message that a GraphQL over WebSocket clients sends
	 * before closing the WebSocket connection.
	 * @return signals the end of completion handling
	 */
	default Mono<Void> handleConnectionCompletion() {
		return Mono.empty();
	}

}

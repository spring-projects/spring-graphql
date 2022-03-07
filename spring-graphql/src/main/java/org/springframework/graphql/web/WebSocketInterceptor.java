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
	 * Handle the {@code "connection_init"} message at the start of a GraphQL over
	 * WebSocket session and return an optional payload for the
	 * {@code "connection_ack"} message to send back.
	 * @param connectionInitPayload the payload from the {@code "connection_init"} message
	 * @return the payload for the {@code "connection_ack"}, or empty
	 */
	default Mono<Object> handleConnectionInitialization(Map<String, Object> connectionInitPayload) {
		return Mono.empty();
	}

	/**
	 * Handle the {@code "complete"} message that clients send to stop listening
	 * to the subscription with the given id.
	 * <p>Note that the {@link org.reactivestreams.Publisher} for the subscription
	 * is automatically cancelled and there is no need to do that from here.
	 * @return {@code Mono} for the completion of handling
	 */
	default Mono<Void> handleCancelledSubscription(String subscriptionId) {
		return Mono.empty();
	}

}

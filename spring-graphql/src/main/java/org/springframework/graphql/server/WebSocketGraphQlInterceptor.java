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
package org.springframework.graphql.server;

import java.util.Map;

import reactor.core.publisher.Mono;


/**
 * An extension of {@link WebGraphQlInterceptor} with additional methods
 * to handle the start and end of a WebSocket connection. Only a single
 * interceptor of type {@link WebSocketGraphQlInterceptor} may be
 * declared.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebSocketGraphQlInterceptor extends WebGraphQlInterceptor {

	@Override
	default Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		return chain.next(request);
	}

	/**
	 * Handle the {@code "connection_init"} message at the start of a GraphQL over
	 * WebSocket session and return an optional payload for the
	 * {@code "connection_ack"} message to send back.
	 * @param sessionInfo information about the underlying WebSocket session
	 * @param connectionInitPayload the payload from the {@code "connection_init"} message
	 * @return the payload for the {@code "connection_ack"}, or empty
	 */
	default Mono<Object> handleConnectionInitialization(
			WebSocketSessionInfo sessionInfo, Map<String, Object> connectionInitPayload) {

		return Mono.empty();
	}

	/**
	 * Handle the {@code "complete"} message that a client sends to stop a
	 * subscription stream. The underlying {@link org.reactivestreams.Publisher}
	 * for the subscription is automatically cancelled. This callback is for any
	 * additional, or more centralized handling across subscriptions.
	 * @param sessionInfo information about the underlying WebSocket session
	 * @param subscriptionId the unique id for the subscription; correlates to the
	 * {@link WebGraphQlRequest#getId() requestId} from the original {@code "subscribe"}
	 * message that started the subscription
	 * @return {@code Mono} for the completion of handling
	 */
	default Mono<Void> handleCancelledSubscription(WebSocketSessionInfo sessionInfo, String subscriptionId) {
		return Mono.empty();
	}

	/**
	 * Invoked when the WebSocket session is closed, from either side.
	 * @param sessionInfo information about the underlying WebSocket session
	 * @param statusCode the WebSocket "close" status code
	 * @param connectionInitPayload the payload from the {@code "connect_init"}
	 * message received at the start of the connection
	 */
	default void handleConnectionClosed(
			WebSocketSessionInfo sessionInfo, int statusCode, Map<String, Object> connectionInitPayload) {
	}

}

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import java.util.Map;

import reactor.core.publisher.Mono;


/**
 * An extension of {@link WebGraphQlInterceptor} with additional methods
 * to handle the start and end of a WebSocket connection, as well as client-side
 * cancellation of subscriptions.
 *
 * <p>Use {@link WebGraphQlHandler.Builder#interceptor(WebGraphQlInterceptor...)}
 * to configure the interceptor chain. Only one interceptor in the chain may be
 * of type {@code WebSocketGraphQlInterceptor}.
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
	 * {@link WebGraphQlRequest#getId() requestId} from the {@code "subscribe"}
	 * message that started the subscription stream
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

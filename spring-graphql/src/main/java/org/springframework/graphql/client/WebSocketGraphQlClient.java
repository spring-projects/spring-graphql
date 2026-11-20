/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import java.net.URI;
import java.time.Duration;

import reactor.core.publisher.Mono;

import org.springframework.web.reactive.socket.client.WebSocketClient;


/**
 * GraphQL over WebSocket client that uses {@link WebSocketClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebSocketGraphQlClient extends WebGraphQlClient {

	/**
	 * Start the GraphQL session by connecting the WebSocket, sending the
	 * "connection_init" and receiving the "connection_ack" message.
	 * <p><strong>Note:</Strong> Only one session is started at a time.
	 * Additional attempts to start have no impact while a session is active.
	 * @return {@code Mono} that completes when the WebSocket is connected and
	 * the GraphQL session is ready to send requests
	 */
	Mono<Void> start();

	/**
	 * Stop the GraphQL session by closing the WebSocket with
	 * {@link org.springframework.web.reactive.socket.CloseStatus#NORMAL} and
	 * terminating in-progress requests with an error signal.
	 * <p>New requests are rejected from the time of this call. If necessary,
	 * call {@link #start()} to allow requests again.
	 * @return {@code Mono} that completes when the underlying session is closed
	 */
	Mono<Void> stop();

	@Override
	Builder<?> mutate();


	/**
	 * Create a {@link WebSocketGraphQlClient}.
	 * @param url the GraphQL endpoint URL
	 * @param webSocketClient the underlying transport client to use
	 */
	static WebSocketGraphQlClient create(URI url, WebSocketClient webSocketClient) {
		return builder(url, webSocketClient).build();
	}

	/**
	 * Return a builder for a {@link WebSocketGraphQlClient}.
	 * @param url the GraphQL endpoint URL
	 * @param webSocketClient the underlying transport client to use
	 */
	static Builder<?> builder(String url, WebSocketClient webSocketClient) {
		return new DefaultWebSocketGraphQlClientBuilder(url, webSocketClient);
	}

	/**
	 * Return a builder for a {@link WebSocketGraphQlClient}.
	 * @param url the GraphQL endpoint URL
	 * @param webSocketClient the underlying transport client to use
	 */
	static Builder<?> builder(URI url, WebSocketClient webSocketClient) {
		return new DefaultWebSocketGraphQlClientBuilder(url, webSocketClient);
	}


	/**
	 * Builder for a GraphQL over WebSocket client.
	 * @param <B> the builder type
	 */
	interface Builder<B extends Builder<B>> extends WebGraphQlClient.Builder<B> {

		/**
		 * Configure how frequently to send ping messages.
		 * <p>By default, this is not set, and ping messages are not sent.
		 * @param keepAlive the value to use
		 * @since 1.3.0
		 */
		Builder<B> keepAlive(Duration keepAlive);

		/**
		 * Build the {@code WebSocketGraphQlClient}.
		 */
		@Override
		WebSocketGraphQlClient build();

	}

}

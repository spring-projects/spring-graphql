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

package org.springframework.graphql.client;

import java.net.URI;

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
	 */
	interface Builder<B extends Builder<B>> extends WebGraphQlClient.Builder<B> {

		/**
		 * Build the {@code WebSocketGraphQlClient}.
		 */
		@Override
		WebSocketGraphQlClient build();

	}

}

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
import java.util.Map;
import java.util.function.Consumer;

import reactor.core.publisher.Mono;

import org.springframework.lang.Nullable;
import org.springframework.web.reactive.socket.client.WebSocketClient;


/**
 * {@code GraphQlClient} for GraphQL over Web via {@link WebSocketClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebSocketGraphQlClient extends GraphQlClient {

	/**
	 * Start the transport by connecting the WebSocket, sending the
	 * "connection_init" and waiting for the "connection_ack" message.
	 * @return {@code Mono} that completes when the WebSocket is connected and
	 * ready to begin sending GraphQL requests
	 */
	Mono<Void> start();

	/**
	 * Stop the transport by closing the WebSocket with
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
	 * Create a {@link WebSocketGraphQlClient} that uses the given
	 * {@code WebSocketClient} to connect to the given URL.
	 * @param url the GraphQL endpoint URL
	 * @param webSocketClient the transport client to use
	 */
	static WebSocketGraphQlClient create(URI url, WebSocketClient webSocketClient) {
		return builder(webSocketClient).url(url).build();
	}

	/**
	 * Return a builder to initialize a {@link WebSocketGraphQlClient} with.
	 * @param webSocketClient the transport client to use
	 */
	static Builder<?> builder(WebSocketClient webSocketClient) {
		return new DefaultWebSocketGraphQlClient.Builder(webSocketClient);
	}


	/**
	 * Builder for a GraphQL over WebSocket client.
	 */
	interface Builder<B extends Builder<B>> extends HttpGraphQlClient.BaseBuilder<B> {

		/**
		 * The payload to send with the "connection_init" message.
		 */
		B connectionInitPayload(@Nullable Object connectionInitPayload);

		/**
		 * Handler for the payload received with the "connection_ack" message.
		 */
		B connectionAckHandler(Consumer<Map<String, Object>> ackHandler);

		/**
		 * Build the {@code WebSocketGraphQlClient}.
		 */
		@Override
		WebSocketGraphQlClient build();

	}

}

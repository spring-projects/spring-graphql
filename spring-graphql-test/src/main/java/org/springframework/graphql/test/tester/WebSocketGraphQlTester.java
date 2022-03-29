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

package org.springframework.graphql.test.tester;

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

/**
 * GraphQL over WebSocket client that uses {@link WebSocketClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebSocketGraphQlTester extends WebGraphQlTester {

	/**
	 * This is delegated to the {@code start()} method of the underlying
	 * {@link WebSocketGraphQlClient}.
	 */
	Mono<Void> start();

	/**
	 * This is delegated to the {@code stop()} method of the underlying
	 * {@link WebSocketGraphQlClient}.
	 */
	Mono<Void> stop();

	@Override
	Builder<?> mutate();


	/**
	 * Create a {@link WebSocketGraphQlTester}.
	 * @param url the GraphQL endpoint URL
	 * @param webSocketClient the underlying transport client to use
	 */
	static WebSocketGraphQlTester create(URI url, WebSocketClient webSocketClient) {
		return builder(url, webSocketClient).build();
	}

	/**
	 * Return a builder for a {@link WebSocketGraphQlClient}.
	 * @param url the GraphQL endpoint URL
	 * @param webSocketClient the underlying transport client to use
	 */
	static WebSocketGraphQlTester.Builder<?> builder(String url, WebSocketClient webSocketClient) {
		return new DefaultWebSocketGraphQlTesterBuilder(url, webSocketClient);
	}

	/**
	 * Return a builder for a {@link WebSocketGraphQlClient}.
	 * @param url the GraphQL endpoint URL
	 * @param webSocketClient the underlying transport client to use
	 */
	static WebSocketGraphQlTester.Builder<?> builder(URI url, WebSocketClient webSocketClient) {
		return new DefaultWebSocketGraphQlTesterBuilder(url, webSocketClient);
	}


	/**
	 * Builder for a GraphQL over WebSocket tester.
	 */
	interface Builder<B extends Builder<B>> extends WebGraphQlTester.Builder<B> {

		/**
		 * Build the {@code WebSocketGraphQlTester}.
		 */
		@Override
		WebSocketGraphQlTester build();

	}

}

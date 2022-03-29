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
import java.util.function.Consumer;

import reactor.core.publisher.Mono;

import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.client.WebSocketClient;


/**
 * Default {@link WebSocketGraphQlTester.Builder} implementation, wraps a
 * {@link WebSocketGraphQlClient.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultWebSocketGraphQlTesterBuilder
		extends AbstractGraphQlTesterBuilder<DefaultWebSocketGraphQlTesterBuilder>
		implements WebSocketGraphQlTester.Builder<DefaultWebSocketGraphQlTesterBuilder> {

	private final WebSocketGraphQlClient.Builder<?> graphQlClientBuilder;


	/**
	 * Constructor to start via {@link WebSocketGraphQlTester#builder(String, WebSocketClient)}.
	 */
	DefaultWebSocketGraphQlTesterBuilder(String url, WebSocketClient webSocketClient) {
		Assert.notNull(webSocketClient, "WebSocketClient is required");
		this.graphQlClientBuilder = WebSocketGraphQlClient.builder(url, webSocketClient);
	}

	/**
	 * Constructor to start via {@link WebSocketGraphQlTester#builder(URI, WebSocketClient)}.
	 */
	DefaultWebSocketGraphQlTesterBuilder(URI url, WebSocketClient webSocketClient) {
		Assert.notNull(webSocketClient, "WebSocketClient is required");
		this.graphQlClientBuilder = WebSocketGraphQlClient.builder(url, webSocketClient);
	}

	/**
	 * Constructor to mutate.
	 * @param client the underlying client with the current state
	 */
	DefaultWebSocketGraphQlTesterBuilder(WebSocketGraphQlClient client) {
		Assert.notNull(client, "WebSocketGraphQlClient is required");
		this.graphQlClientBuilder = client.mutate();
	}


	@Override
	public DefaultWebSocketGraphQlTesterBuilder url(String url) {
		this.graphQlClientBuilder.url(url);
		return this;
	}

	@Override
	public DefaultWebSocketGraphQlTesterBuilder url(URI url) {
		this.graphQlClientBuilder.url(url);
		return this;
	}

	@Override
	public DefaultWebSocketGraphQlTesterBuilder header(String name, String... values) {
		this.graphQlClientBuilder.header(name, values);
		return this;
	}

	@Override
	public DefaultWebSocketGraphQlTesterBuilder headers(Consumer<HttpHeaders> headersConsumer) {
		this.graphQlClientBuilder.headers(headersConsumer);
		return this;
	}

	@Override
	public DefaultWebSocketGraphQlTesterBuilder codecConfigurer(Consumer<CodecConfigurer> codecsConsumer) {
		this.graphQlClientBuilder.codecConfigurer(codecsConsumer);
		return this;
	}

	@Override
	public WebSocketGraphQlTester build() {
		registerJsonPathMappingProvider();
		WebSocketGraphQlClient client = this.graphQlClientBuilder.build();
		GraphQlTester graphQlTester = super.buildGraphQlTester(asTransport(client));
		return new DefaultWebSocketGraphQlTester(graphQlTester, client, getBuilderInitializer());
	}

	private void registerJsonPathMappingProvider() {
		this.graphQlClientBuilder.codecConfigurer(codecConfigurer -> {
			configureJsonPathConfig(jsonPathConfig -> {
				EncoderDecoderMappingProvider provider = new EncoderDecoderMappingProvider(codecConfigurer);
				return jsonPathConfig.mappingProvider(provider);
			});
		});
	}


	/**
	 * Default {@link WebSocketGraphQlTester} implementation.
	 */
	private static class DefaultWebSocketGraphQlTester extends AbstractDelegatingGraphQlTester implements WebSocketGraphQlTester {

		private final WebSocketGraphQlClient client;

		private final Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer;

		private DefaultWebSocketGraphQlTester(
				GraphQlTester graphQlTester, WebSocketGraphQlClient client,
				Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer) {

			super(graphQlTester);
			this.client = client;
			this.builderInitializer = builderInitializer;
		}

		@Override
		public Mono<Void> start() {
			return this.client.start();
		}

		@Override
		public Mono<Void> stop() {
			return this.client.stop();
		}

		@Override
		public DefaultWebSocketGraphQlTesterBuilder mutate() {
			DefaultWebSocketGraphQlTesterBuilder builder = new DefaultWebSocketGraphQlTesterBuilder(this.client);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}

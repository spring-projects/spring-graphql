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
import java.util.Arrays;
import java.util.function.Consumer;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.util.DefaultUriBuilderFactory;


/**
 * Default {@link WebSocketGraphQlClient} implementation that builds the underlying
 * {@code WebSocketGraphQlTransport} to use.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultWebSocketGraphQlClient extends AbstractDelegatingGraphQlClient implements WebSocketGraphQlClient {

	private final WebSocketGraphQlTransport transport;

	private final Consumer<GraphQlClient.Builder<?>> builderInitializer;


	DefaultWebSocketGraphQlClient(GraphQlClient delegate, WebSocketGraphQlTransport transport,
			Consumer<GraphQlClient.Builder<?>> builderInitializer) {

		super(delegate);

		Assert.notNull(transport, "WebSocketGraphQlTransport is required");
		Assert.notNull(builderInitializer, "`builderInitializer` is required");

		this.transport = transport;
		this.builderInitializer = builderInitializer;
	}


	@Override
	public Mono<Void> start() {
		return this.transport.start();
	}

	@Override
	public Mono<Void> stop() {
		return this.transport.stop();
	}

	@Override
	public Builder mutate() {
		Builder builder = new Builder(this.transport);
		this.builderInitializer.accept(builder);
		return builder;
	}


	/**
	 * Default {@link WebSocketGraphQlClient.Builder} implementation.
	 */
	static final class Builder extends AbstractGraphQlClientBuilder<Builder>
			implements WebSocketGraphQlClient.Builder<Builder> {

		private URI url;

		private final HttpHeaders headers = new HttpHeaders();

		private final WebSocketClient webSocketClient;

		private final CodecConfigurer codecConfigurer;

		/**
		 * Constructor to start via {@link WebSocketGraphQlClient#builder(URI, WebSocketClient)}.
		 */
		Builder(URI url, WebSocketClient client) {
			this.url = url;
			this.webSocketClient = client;
			this.codecConfigurer = ClientCodecConfigurer.create();
		}

		/**
		 * Constructor to mutate.
		 * @param transport the underlying transport with the current state
		 */
		Builder(WebSocketGraphQlTransport transport) {
			this.url = transport.getUrl();
			this.headers.putAll(transport.getHeaders());
			this.webSocketClient = transport.getWebSocketClient();
			this.codecConfigurer = transport.getCodecConfigurer();
		}

		@Override
		public Builder url(String url) {
			url(new DefaultUriBuilderFactory().uriString(url).build());
			return this;
		}

		@Override
		public Builder url(URI url) {
			this.url = url;
			return this;
		}

		@Override
		public Builder header(String name, String... values) {
			this.headers.put(name, Arrays.asList(values));
			return this;
		}

		@Override
		public Builder headers(Consumer<HttpHeaders> headersConsumer) {
			headersConsumer.accept(this.headers);
			return this;
		}

		@Override
		public Builder codecConfigurer(Consumer<CodecConfigurer> codecConsumer) {
			codecConsumer.accept(this.codecConfigurer);
			return this;
		}

		@Override
		public WebSocketGraphQlClient build() {

			WebSocketGraphQlTransport transport = new WebSocketGraphQlTransport(
					this.url, this.headers, this.webSocketClient, this.codecConfigurer, null, payload -> {});

			GraphQlClient graphQlClient = super.buildGraphQlClient(transport);
			return new DefaultWebSocketGraphQlClient(graphQlClient, transport, getBuilderInitializer());
		}

	}

}

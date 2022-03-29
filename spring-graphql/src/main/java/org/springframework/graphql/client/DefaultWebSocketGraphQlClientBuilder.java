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
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.util.DefaultUriBuilderFactory;


/**
 * Default {@link WebSocketGraphQlClient.Builder} that builds the underlying
 * {@code WebSocketGraphQlTransport}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultWebSocketGraphQlClientBuilder
		extends AbstractGraphQlClientBuilder<DefaultWebSocketGraphQlClientBuilder>
		implements WebSocketGraphQlClient.Builder<DefaultWebSocketGraphQlClientBuilder> {

	private URI url;

	private final HttpHeaders headers = new HttpHeaders();

	private final WebSocketClient webSocketClient;

	private final CodecConfigurer codecConfigurer;


	/**
	 * Constructor to start via {@link WebSocketGraphQlClient#builder(String, WebSocketClient)}.
	 */
	DefaultWebSocketGraphQlClientBuilder(String url, WebSocketClient client) {
		this(toURI(url), client);
	}

	/**
	 * Constructor to start via {@link WebSocketGraphQlClient#builder(URI, WebSocketClient)}.
	 */
	DefaultWebSocketGraphQlClientBuilder(URI url, WebSocketClient client) {
		this.url = url;
		this.webSocketClient = client;
		this.codecConfigurer = ClientCodecConfigurer.create();
	}

	/**
	 * Constructor to mutate.
	 * @param transport the underlying transport with the current state
	 */
	DefaultWebSocketGraphQlClientBuilder(WebSocketGraphQlTransport transport) {
		this.url = transport.getUrl();
		this.headers.putAll(transport.getHeaders());
		this.webSocketClient = transport.getWebSocketClient();
		this.codecConfigurer = transport.getCodecConfigurer();
	}


	@Override
	public DefaultWebSocketGraphQlClientBuilder url(String url) {
		return url(toURI(url));
	}

	@Override
	public DefaultWebSocketGraphQlClientBuilder url(URI url) {
		this.url = url;
		return this;
	}

	private static URI toURI(String url) {
		return new DefaultUriBuilderFactory().uriString(url).build();
	}

	@Override
	public DefaultWebSocketGraphQlClientBuilder header(String name, String... values) {
		this.headers.put(name, Arrays.asList(values));
		return this;
	}

	@Override
	public DefaultWebSocketGraphQlClientBuilder headers(Consumer<HttpHeaders> headersConsumer) {
		headersConsumer.accept(this.headers);
		return this;
	}

	@Override
	public DefaultWebSocketGraphQlClientBuilder codecConfigurer(Consumer<CodecConfigurer> codecConfigurerConsumer) {
		codecConfigurerConsumer.accept(this.codecConfigurer);
		return this;
	}

	@Override
	public WebSocketGraphQlClient build() {

		setJsonCodecs(
				CodecDelegate.findJsonEncoder(this.codecConfigurer),
				CodecDelegate.findJsonDecoder(this.codecConfigurer));

		WebSocketGraphQlTransport transport = new WebSocketGraphQlTransport(
				this.url, this.headers, this.webSocketClient, this.codecConfigurer, getInterceptor());

		GraphQlClient graphQlClient = super.buildGraphQlClient(transport);
		return new DefaultWebSocketGraphQlClient(graphQlClient, transport, getBuilderInitializer());
	}

	private WebSocketGraphQlClientInterceptor getInterceptor() {

		List<WebSocketGraphQlClientInterceptor> interceptors = getInterceptors().stream()
				.filter(interceptor -> interceptor instanceof WebSocketGraphQlClientInterceptor)
				.map(interceptor -> (WebSocketGraphQlClientInterceptor) interceptor)
				.collect(Collectors.toList());

		Assert.state(interceptors.size() <= 1,
				"Only a single interceptor of type WebSocketGraphQlClientInterceptor may be configured");

		return (!interceptors.isEmpty() ? interceptors.get(0) : new WebSocketGraphQlClientInterceptor() {});
	}


	/**
	 * Default {@link WebSocketGraphQlClient} implementation.
	 */
	private static class DefaultWebSocketGraphQlClient extends AbstractDelegatingGraphQlClient implements WebSocketGraphQlClient {

		private final WebSocketGraphQlTransport transport;

		private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;

		DefaultWebSocketGraphQlClient(GraphQlClient delegate, WebSocketGraphQlTransport transport,
				Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

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
		public DefaultWebSocketGraphQlClientBuilder mutate() {
			DefaultWebSocketGraphQlClientBuilder builder = new DefaultWebSocketGraphQlClientBuilder(this.transport);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}

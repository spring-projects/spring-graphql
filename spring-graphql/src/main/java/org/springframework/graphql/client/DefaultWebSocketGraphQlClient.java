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

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;

import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.client.WebSocketClient;


/**
 * Default {@link WebSocketGraphQlClient} implementation.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultWebSocketGraphQlClient extends AbstractDelegatingGraphQlClient implements WebSocketGraphQlClient {

	private final WebSocketGraphQlTransport transport;

	private final Supplier<Builder> mutateBuilderFactory;


	DefaultWebSocketGraphQlClient(
			GraphQlClient delegate, WebSocketGraphQlTransport transport, Supplier<Builder> mutateBuilderFactory) {

		super(delegate);
		this.transport = transport;
		this.mutateBuilderFactory = mutateBuilderFactory;
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
		return this.mutateBuilderFactory.get();
	}


	/**
	 * Default {@link WebSocketGraphQlClient.Builder} implementation.
	 */
	static final class Builder extends DefaultHttpGraphQlClient.BaseBuilder<Builder>
			implements WebSocketGraphQlClient.Builder<Builder> {

		private final WebSocketClient webSocketClient;

		@Nullable
		private Object initPayload;

		private Consumer<Map<String, Object>> connectionAckHandler = ackPayload -> {};


		Builder(WebSocketClient client) {
			this.webSocketClient = client;
		}


		@Override
		public Builder connectionInitPayload(@Nullable Object connectionInitPayload) {
			this.initPayload = connectionInitPayload;
			return this;
		}

		@Override
		public Builder connectionAckHandler(Consumer<Map<String, Object>> ackHandler) {
			this.connectionAckHandler = ackHandler;
			return this;
		}

		@Override
		public WebSocketGraphQlClient build() {
			Assert.notNull(getUrl(), "GraphQL endpoint URI is required");

			WebSocketGraphQlTransport transport = new WebSocketGraphQlTransport(
					getUrl(), getHeaders(), this.webSocketClient, initClientCodecConfigurer(),
					this.initPayload, this.connectionAckHandler);

			transport(transport);
			GraphQlClient graphQlClient = super.build();

			return new DefaultWebSocketGraphQlClient(graphQlClient, transport, mutateBuilderFactory());
		}

		private ClientCodecConfigurer initClientCodecConfigurer() {
			ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
			if (getCodecConfigurerConsumer() != null) {
				getCodecConfigurerConsumer().accept(configurer);
			}
			return configurer;
		}

		private Supplier<Builder> mutateBuilderFactory() {
			Consumer<HttpGraphQlClient.BaseBuilder<?>> parentBuilderInitializer = getWebBuilderInitializer();
			return () -> {
				Builder builder = new Builder(this.webSocketClient);
				builder.connectionInitPayload(this.initPayload);
				builder.connectionAckHandler(this.connectionAckHandler);
				parentBuilderInitializer.accept(builder);
				return builder;
			};
		}

	}

}

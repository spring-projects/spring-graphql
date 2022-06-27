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
import java.util.function.Consumer;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import reactor.core.publisher.Mono;

import org.springframework.lang.Nullable;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;


/**
 * Default {@link RSocketGraphQlClient.Builder} implementation that wraps
 * a {@link RSocketRequester.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultRSocketGraphQlClientBuilder
		extends AbstractGraphQlClientBuilder<DefaultRSocketGraphQlClientBuilder>
		implements RSocketGraphQlClient.Builder<DefaultRSocketGraphQlClientBuilder> {

	private final RSocketRequester.Builder requesterBuilder;

	@Nullable
	private ClientTransport clientTransport;

	private String route;


	DefaultRSocketGraphQlClientBuilder() {
		this(initRSocketRequestBuilder());
	}

	DefaultRSocketGraphQlClientBuilder(RSocketRequester.Builder requesterBuilder) {
		Assert.notNull(requesterBuilder, "RSocketRequester.Builder is required");
		this.requesterBuilder = requesterBuilder;
		this.route = "graphql";
	}

	private static RSocketRequester.Builder initRSocketRequestBuilder() {
		RSocketRequester.Builder requesterBuilder = RSocketRequester.builder().dataMimeType(MimeTypeUtils.APPLICATION_JSON);
		if (jackson2Present) {
			requesterBuilder.rsocketStrategies(
					RSocketStrategies.builder()
							.encoder(DefaultJackson2Codecs.encoder())
							.decoder(DefaultJackson2Codecs.decoder())
							.build());
		}
		return requesterBuilder;
	}


	@Override
	public DefaultRSocketGraphQlClientBuilder tcp(String host, int port) {
		this.clientTransport = TcpClientTransport.create(host, port);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlClientBuilder webSocket(URI uri) {
		this.clientTransport = WebsocketClientTransport.create(uri);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlClientBuilder clientTransport(ClientTransport clientTransport) {
		this.clientTransport = clientTransport;
		return this;
	}

	@Override
	public DefaultRSocketGraphQlClientBuilder dataMimeType(MimeType dataMimeType) {
		this.requesterBuilder.dataMimeType(dataMimeType);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlClientBuilder route(String route) {
		Assert.notNull(route, "'route' is required");
		this.route = route;
		return this;
	}

	@Override
	public DefaultRSocketGraphQlClientBuilder rsocketRequester(Consumer<RSocketRequester.Builder> requesterConsumer) {
		requesterConsumer.accept(this.requesterBuilder);
		return this;
	}

	@Override
	public RSocketGraphQlClient build() {

		// Pass the codecs to the parent for response decoding
		this.requesterBuilder.rsocketStrategies(builder -> {
			builder.decoders(decoders -> setJsonDecoder(CodecDelegate.findJsonDecoder(decoders)));
			builder.encoders(encoders -> setJsonEncoder(CodecDelegate.findJsonEncoder(encoders)));
		});

		Assert.state(this.clientTransport != null, "Neither WebSocket nor TCP networking configured");
		RSocketRequester requester = this.requesterBuilder.transport(this.clientTransport);
		RSocketGraphQlTransport graphQlTransport = new RSocketGraphQlTransport(this.route, requester, getJsonDecoder());

		return new DefaultRSocketGraphQlClient(
				super.buildGraphQlClient(graphQlTransport), requester,
				this.requesterBuilder, this.clientTransport, this.route, getBuilderInitializer());
	}


	/**
	 * Default {@link RSocketGraphQlClient} implementation.
	 */
	private static class DefaultRSocketGraphQlClient extends AbstractDelegatingGraphQlClient implements RSocketGraphQlClient {

		private final RSocketRequester requester;

		private final RSocketRequester.Builder requesterBuilder;

		private final ClientTransport clientTransport;

		private final String route;

		private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;

		DefaultRSocketGraphQlClient(
				GraphQlClient graphQlClient, RSocketRequester requester, RSocketRequester.Builder requesterBuilder,
				ClientTransport clientTransport, String route, Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

			super(graphQlClient);

			this.requester = requester;
			this.requesterBuilder = requesterBuilder;
			this.clientTransport = clientTransport;
			this.route = route;
			this.builderInitializer = builderInitializer;
		}

		@Override
		public Mono<Void> start() {
			return this.requester.rsocketClient().source().then();
		}

		@Override
		public Mono<Void> stop() {
			// Currently, no option to close and wait (see Javadoc)
			this.requester.dispose();
			return Mono.empty();
		}

		@Override
		public RSocketGraphQlClient.Builder<?> mutate() {
			DefaultRSocketGraphQlClientBuilder builder = new DefaultRSocketGraphQlClientBuilder(this.requesterBuilder);
			builder.clientTransport(this.clientTransport);
			builder.route(this.route);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}

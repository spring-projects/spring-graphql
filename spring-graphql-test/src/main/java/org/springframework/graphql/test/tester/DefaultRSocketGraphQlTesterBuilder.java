/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import io.rsocket.transport.ClientTransport;
import reactor.core.publisher.Mono;

import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.client.RSocketGraphQlClient;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.util.MimeType;


/**
 * Default implementation of {@link RSocketGraphQlTester.Builder} that wraps
 * an {@link RSocketGraphQlClient.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultRSocketGraphQlTesterBuilder
		extends AbstractGraphQlTesterBuilder<DefaultRSocketGraphQlTesterBuilder>
		implements RSocketGraphQlTester.Builder<DefaultRSocketGraphQlTesterBuilder> {

	private final RSocketGraphQlClient.Builder<?> rsocketGraphQlClientBuilder;


	/**
	 * Constructor to start via {@link RSocketGraphQlTester#builder()}.
	 */
	DefaultRSocketGraphQlTesterBuilder() {
		this.rsocketGraphQlClientBuilder = RSocketGraphQlClient.builder();
	}

	/**
	 * Constructor to start via {@link RSocketGraphQlTester#builder(RSocketRequester.Builder)}.
	 */
	DefaultRSocketGraphQlTesterBuilder(RSocketRequester.Builder requesterBuilder) {
		this.rsocketGraphQlClientBuilder = RSocketGraphQlClient.builder(requesterBuilder);
	}

	/**
	 * Constructor to mutate.
	 * @param rsocketGraphQlClient the underlying client with the current state
	 */
	public DefaultRSocketGraphQlTesterBuilder(RSocketGraphQlClient rsocketGraphQlClient) {
		this.rsocketGraphQlClientBuilder = rsocketGraphQlClient.mutate();
	}


	@Override
	public DefaultRSocketGraphQlTesterBuilder tcp(String host, int port) {
		this.rsocketGraphQlClientBuilder.tcp(host, port);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlTesterBuilder webSocket(URI uri) {
		this.rsocketGraphQlClientBuilder.webSocket(uri);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlTesterBuilder clientTransport(ClientTransport clientTransport) {
		this.rsocketGraphQlClientBuilder.clientTransport(clientTransport);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlTesterBuilder dataMimeType(MimeType dataMimeType) {
		this.rsocketGraphQlClientBuilder.dataMimeType(dataMimeType);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlTesterBuilder route(String route) {
		this.rsocketGraphQlClientBuilder.route(route);
		return this;
	}

	@Override
	public DefaultRSocketGraphQlTesterBuilder rsocketRequester(Consumer<RSocketRequester.Builder> requesterConsumer) {
		this.rsocketGraphQlClientBuilder.rsocketRequester(requesterConsumer);
		return this;
	}

	@Override
	public RSocketGraphQlTester build() {
		registerJsonPathMappingProvider();
		RSocketGraphQlClient rsocketGraphQlClient = this.rsocketGraphQlClientBuilder.build();
		GraphQlTester graphQlTester = super.buildGraphQlTester(asTransport(rsocketGraphQlClient));
		return new DefaultRSocketGraphQlTester(graphQlTester, rsocketGraphQlClient, getBuilderInitializer());
	}

	private void registerJsonPathMappingProvider() {
		this.rsocketGraphQlClientBuilder.rsocketRequester((builder) ->
				builder.rsocketStrategies((strategiesBuilder) ->
						configureJsonPathConfig((config) -> {
							RSocketStrategies strategies = strategiesBuilder.build();
							List<Encoder<?>> encoders = strategies.encoders();
							List<Decoder<?>> decoders = strategies.decoders();
							return config.mappingProvider(new EncoderDecoderMappingProvider(encoders, decoders));
						})));
	}


	/**
	 * Default {@link RSocketGraphQlTester} implementation.
	 */
	private static class DefaultRSocketGraphQlTester extends AbstractDelegatingGraphQlTester implements RSocketGraphQlTester {

		private final RSocketGraphQlClient rsocketGraphQlClient;

		private final Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer;

		DefaultRSocketGraphQlTester(
				GraphQlTester delegate, RSocketGraphQlClient rsocketGraphQlClient,
				Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer) {

			super(delegate);
			this.rsocketGraphQlClient = rsocketGraphQlClient;
			this.builderInitializer = builderInitializer;
		}

		@Override
		public Mono<Void> start() {
			return this.rsocketGraphQlClient.start();
		}

		@Override
		public Mono<Void> stop() {
			return this.rsocketGraphQlClient.stop();
		}

		@Override
		public RSocketGraphQlTester.Builder<?> mutate() {
			DefaultRSocketGraphQlTesterBuilder builder = new DefaultRSocketGraphQlTesterBuilder(this.rsocketGraphQlClient);
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}

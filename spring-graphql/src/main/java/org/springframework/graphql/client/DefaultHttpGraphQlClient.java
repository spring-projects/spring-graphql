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
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Default {@link HttpGraphQlClient} implementation.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultHttpGraphQlClient extends AbstractDelegatingGraphQlClient implements HttpGraphQlClient {

	private final Supplier<Builder> mutateBuilder;


	DefaultHttpGraphQlClient(GraphQlClient graphQlClient, Supplier<Builder> mutateBuilder) {
		super(graphQlClient);
		this.mutateBuilder = mutateBuilder;
	}


	public Builder mutate() {
		return this.mutateBuilder.get();
	}


	static class BaseBuilder<B extends BaseBuilder<B>> extends DefaultGraphQlClientBuilder<B>
			implements HttpGraphQlClient.BaseBuilder<B> {

		@Nullable
		private URI url;

		private final HttpHeaders headers = new HttpHeaders();

		@Nullable
		private Consumer<ClientCodecConfigurer> codecConfigurerConsumer;


		@Override
		public B url(@Nullable String url) {
			this.url = (url != null ? URI.create(url) : null);
			return self();
		}

		@Override
		public B url(@Nullable URI url) {
			this.url = url;
			return self();
		}

		@Override
		public B header(String name, String... values) {
			Arrays.stream(values).forEach(value -> this.headers.add(name, value));
			return self();
		}

		@Override
		public B headers(Consumer<HttpHeaders> headersConsumer) {
			headersConsumer.accept(this.headers);
			return self();
		}

		@Override
		public B codecConfigurer(Consumer<ClientCodecConfigurer> codecConsumer) {
			this.codecConfigurerConsumer = codecConsumer;
			return self();
		}

		@Nullable
		protected URI getUrl() {
			return this.url;
		}

		protected HttpHeaders getHeaders() {
			return this.headers;
		}

		@Nullable
		protected Consumer<ClientCodecConfigurer> getCodecConfigurerConsumer() {
			return this.codecConfigurerConsumer;
		}

		@SuppressWarnings("unchecked")
		private <T extends B> T self() {
			return (T) this;
		}

		/**
		 * Exposes a {@code Consumer} to subclasses to initialize new builder instances
		 * from the configuration of "this" builder.
		 */
		protected Consumer<HttpGraphQlClient.BaseBuilder<?>> getWebBuilderInitializer() {
			Consumer<GraphQlClient.Builder<?>> parentInitializer = getBuilderInitializer();
			HttpHeaders headersCopy = new HttpHeaders();
			headersCopy.putAll(getHeaders());
			return builder -> {
				builder.url(getUrl()).headers(headers -> headers.putAll(headersCopy));
				if (getCodecConfigurerConsumer() != null) {
					builder.codecConfigurer(getCodecConfigurerConsumer());
				}
				parentInitializer.accept(builder);
			};
		}

	}


	/**
	 * Default {@link HttpGraphQlClient.Builder} implementation.
	 */
	static final class Builder extends BaseBuilder<Builder> implements HttpGraphQlClient.Builder<Builder> {

		@Nullable
		private WebClient webClient;

		@Nullable
		private Consumer<WebClient.Builder> webClientConfigurers;


		/**
		 * Constructor to start without a WebClient instance.
		 */
		Builder() {
		}

		/**
		 * Constructor to start with a pre-configured {@code WebClient}.
		 */
		Builder(WebClient webClient) {
			this.webClient = webClient;
		}


		@Override
		public Builder webClient(Consumer<WebClient.Builder> configurer) {
			this.webClientConfigurers = (this.webClientConfigurers != null ? this.webClientConfigurers.andThen(configurer) : configurer);
			return this;
		}

		@Override
		public HttpGraphQlClient build() {

			WebClient webClient = initWebClient();
			HttpGraphQlTransport transport = new HttpGraphQlTransport(webClient);
			transport(transport);

			GraphQlClient graphQlClient = super.build();
			return new DefaultHttpGraphQlClient(graphQlClient, initMutateBuilderFactory(webClient));
		}

		private WebClient initWebClient() {
			WebClient.Builder builder = (this.webClient != null ? this.webClient.mutate() : WebClient.builder());

			if (getUrl() != null) {
				builder.baseUrl(getUrl().toASCIIString());
			}

			builder.defaultHeaders(headers -> headers.putAll(getHeaders()));

			if (getCodecConfigurerConsumer() != null) {
				builder.codecs(getCodecConfigurerConsumer());
			}

			if (this.webClientConfigurers != null) {
				this.webClientConfigurers.accept(builder);
			}

			return builder.build();
		}

		private Supplier<Builder> initMutateBuilderFactory(WebClient webClient) {
			Consumer<HttpGraphQlClient.BaseBuilder<?>> parentInitializer = getWebBuilderInitializer();
			return () -> {
				Builder builder = new Builder(webClient);
				parentInitializer.accept(builder);
				return builder;
			};
		}

	}

}

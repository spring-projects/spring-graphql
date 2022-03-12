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

import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Default {@link HttpGraphQlClient} implementation that builds the underlying
 * {@code HttpGraphQlTransport} to use.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultHttpGraphQlClient extends AbstractDelegatingGraphQlClient implements HttpGraphQlClient {

	private final WebClient webClient;

	private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;


	DefaultHttpGraphQlClient(GraphQlClient graphQlClient, WebClient webClient,
			Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

		super(graphQlClient);

		Assert.notNull(webClient, "WebClient is required");
		Assert.notNull(builderInitializer, "`builderInitializer` is required");

		this.webClient = webClient;
		this.builderInitializer = builderInitializer;
	}


	public Builder mutate() {
		Builder builder = new Builder(this.webClient);
		this.builderInitializer.accept(builder);
		return builder;
	}


	/**
	 * Default {@link HttpGraphQlClient.Builder} implementation, simply wrapping
	 * and delegating to {@link WebClient.Builder}.
	 */
	static final class Builder extends AbstractGraphQlClientBuilder<Builder> implements HttpGraphQlClient.Builder<Builder> {

		private final WebClient.Builder webClientBuilder;

		/**
		 * Constructor to start without a WebClient instance.
		 */
		Builder() {
			this(WebClient.builder());
		}

		/**
		 * Constructor to start with a pre-configured {@code WebClient}.
		 */
		Builder(WebClient client) {
			this(client.mutate());
		}

		/**
		 * Constructor to start with a pre-configured {@code WebClient}.
		 */
		Builder(WebClient.Builder clientBuilder) {
			this.webClientBuilder = clientBuilder;
		}

		@Override
		public Builder url(String url) {
			this.webClientBuilder.baseUrl(url);
			return this;
		}

		@Override
		public Builder url(URI url) {
			UriBuilderFactory factory = new DefaultUriBuilderFactory(UriComponentsBuilder.fromUri(url));
			this.webClientBuilder.uriBuilderFactory(factory);
			return this;
		}

		@Override
		public Builder header(String name, String... values) {
			this.webClientBuilder.defaultHeader(name, values);
			return this;
		}

		@Override
		public Builder headers(Consumer<HttpHeaders> headersConsumer) {
			this.webClientBuilder.defaultHeaders(headersConsumer);
			return this;
		}

		@Override
		public Builder codecConfigurer(Consumer<CodecConfigurer> codecConfigurerConsumer) {
			this.webClientBuilder.codecs(codecConfigurerConsumer::accept);
			return this;
		}

		@Override
		public Builder webClient(Consumer<WebClient.Builder> configurer) {
			configurer.accept(this.webClientBuilder);
			return this;
		}

		@Override
		public HttpGraphQlClient build() {

			registerJsonPathMappingProvider();
			WebClient webClient = this.webClientBuilder.build();

			GraphQlClient graphQlClient = super.buildGraphQlClient(new HttpGraphQlTransport(webClient));
			return new DefaultHttpGraphQlClient(graphQlClient, webClient, getBuilderInitializer());
		}

		private void registerJsonPathMappingProvider() {
			this.webClientBuilder.codecs(codecConfigurer ->
					configureJsonPathConfig(config -> {
						CodecMappingProvider provider = new CodecMappingProvider(codecConfigurer);
						return config.mappingProvider(provider);
					}));
		}

	}

}

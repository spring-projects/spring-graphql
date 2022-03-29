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

import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * Default {@link HttpGraphQlTester.Builder} implementation, simply wraps a
 * {@link WebTestClient.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultHttpGraphQlTesterBuilder
		extends AbstractGraphQlTesterBuilder<DefaultHttpGraphQlTesterBuilder>
		implements HttpGraphQlTester.Builder<DefaultHttpGraphQlTesterBuilder> {

	private final WebTestClient.Builder webTestClientBuilder;


	DefaultHttpGraphQlTesterBuilder(WebTestClient.Builder clientBuilder) {
		this.webTestClientBuilder = clientBuilder;
	}


	@Override
	public DefaultHttpGraphQlTesterBuilder url(String url) {
		this.webTestClientBuilder.baseUrl(url);
		return this;
	}

	@Override
	public DefaultHttpGraphQlTesterBuilder url(URI url) {
		UriBuilderFactory factory = new DefaultUriBuilderFactory(UriComponentsBuilder.fromUri(url));
		this.webTestClientBuilder.uriBuilderFactory(factory);
		return this;
	}

	@Override
	public DefaultHttpGraphQlTesterBuilder header(String name, String... values) {
		this.webTestClientBuilder.defaultHeader(name, values);
		return this;
	}

	@Override
	public DefaultHttpGraphQlTesterBuilder headers(Consumer<HttpHeaders> headersConsumer) {
		this.webTestClientBuilder.defaultHeaders(headersConsumer);
		return this;
	}

	@Override
	public DefaultHttpGraphQlTesterBuilder codecConfigurer(Consumer<CodecConfigurer> codecConsumer) {
		this.webTestClientBuilder.codecs(codecConsumer::accept);
		return this;
	}

	@Override
	public DefaultHttpGraphQlTesterBuilder webTestClient(Consumer<WebTestClient.Builder> configurer) {
		configurer.accept(this.webTestClientBuilder);
		return this;
	}

	@Override
	public HttpGraphQlTester build() {
		registerJsonPathMappingProvider();
		WebTestClient client = this.webTestClientBuilder.build();
		GraphQlTester tester = super.buildGraphQlTester(new WebTestClientTransport(client));
		return new DefaultHttpGraphQlTester(tester, client, getBuilderInitializer());
	}

	private void registerJsonPathMappingProvider() {
		this.webTestClientBuilder.codecs(codecConfigurer ->
				configureJsonPathConfig(config -> {
					EncoderDecoderMappingProvider provider = new EncoderDecoderMappingProvider(codecConfigurer);
					return config.mappingProvider(provider);
				}));
	}


	/**
	 * Default {@link HttpGraphQlTester} that builds and uses a {@link WebTestClient}
	 * for request execution.
	 */
	private static class DefaultHttpGraphQlTester extends AbstractDelegatingGraphQlTester implements HttpGraphQlTester {

		private final WebTestClient webTestClient;

		private final Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer;

		private DefaultHttpGraphQlTester(GraphQlTester graphQlTester, WebTestClient webTestClient,
				Consumer<AbstractGraphQlTesterBuilder<?>> builderInitializer) {

			super(graphQlTester);
			this.webTestClient = webTestClient;
			this.builderInitializer = builderInitializer;
		}

		@Override
		public DefaultHttpGraphQlTesterBuilder mutate() {
			DefaultHttpGraphQlTesterBuilder builder = new DefaultHttpGraphQlTesterBuilder(this.webTestClient.mutate());
			this.builderInitializer.accept(builder);
			return builder;
		}

	}

}

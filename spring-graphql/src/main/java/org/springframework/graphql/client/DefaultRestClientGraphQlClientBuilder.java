/*
 * Copyright 2002-2024 the original author or authors.
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
import java.util.List;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * Default {@link RestClientGraphQlClient.Builder} implementation, a simple wrapper
 * around a {@link RestClient.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
final class DefaultRestClientGraphQlClientBuilder
		extends AbstractGraphQlClientBuilder<DefaultRestClientGraphQlClientBuilder>
		implements RestClientGraphQlClient.Builder<DefaultRestClientGraphQlClientBuilder>  {

	private final RestClient.Builder restClientBuilder;

	@Nullable
	private CodecConfigurer codecConfigurer;

	/**
	 * Constructor to start without a RestClient instance.
	 */
	DefaultRestClientGraphQlClientBuilder() {
		this(RestClient.builder());
	}

	/**
	 * Constructor to start with a pre-configured {@code RestClient}.
	 */
	DefaultRestClientGraphQlClientBuilder(RestClient client) {
		this(client.mutate());
	}

	/**
	 * Constructor to start with a pre-configured {@code RestClient}.
	 */
	DefaultRestClientGraphQlClientBuilder(RestClient.Builder clientBuilder) {
		this.restClientBuilder = clientBuilder;
	}


	@Override
	public DefaultRestClientGraphQlClientBuilder url(String url) {
		this.restClientBuilder.baseUrl(url);
		return this;
	}

	@Override
	public DefaultRestClientGraphQlClientBuilder url(URI url) {
		UriBuilderFactory factory = new DefaultUriBuilderFactory(UriComponentsBuilder.fromUri(url));
		this.restClientBuilder.uriBuilderFactory(factory);
		return this;
	}

	@Override
	public DefaultRestClientGraphQlClientBuilder header(String name, String... values) {
		this.restClientBuilder.defaultHeader(name, values);
		return this;
	}

	@Override
	public DefaultRestClientGraphQlClientBuilder headers(Consumer<HttpHeaders> headersConsumer) {
		this.restClientBuilder.defaultHeaders(headersConsumer);
		return this;
	}

	@Override
	public DefaultRestClientGraphQlClientBuilder codecConfigurer(Consumer<CodecConfigurer> codecConsumer) {
		if (this.codecConfigurer == null) {
			this.codecConfigurer = ClientCodecConfigurer.create();
		}
		codecConsumer.accept(this.codecConfigurer);
		return this;
	}

	@Override
	public DefaultRestClientGraphQlClientBuilder messageConverters(Consumer<List<HttpMessageConverter<?>>> configurer) {
		this.restClientBuilder.messageConverters(configurer);
		return this;
	}

	@Override
	public DefaultRestClientGraphQlClientBuilder restClient(Consumer<RestClient.Builder> configurer) {
		configurer.accept(this.restClientBuilder);
		return this;
	}

	@Override
	public RestClientGraphQlClient build() {

		// Pass the codecs to the parent for response decoding
		if (this.codecConfigurer != null) {
			setJsonEncoder(CodecDelegate.findJsonEncoder(this.codecConfigurer));
			setJsonDecoder(CodecDelegate.findJsonDecoder(this.codecConfigurer));
		}
		else {
			this.restClientBuilder.messageConverters(converters -> {
				setJsonEncoder(HttpMessageConverterDelegate.getJsonEncoder(converters));
				setJsonDecoder(HttpMessageConverterDelegate.getJsonDecoder(converters));
			});
		}

		RestClient restClient = this.restClientBuilder.build();

		GraphQlClient graphQlClient = super.buildGraphQlClient(new RestClientGraphQlTransport(restClient, null));
		return new DefaultRestClientGraphQlClient(graphQlClient, restClient, getBuilderInitializer());
	}


	/**
	 * Default {@link HttpGraphQlClient} implementation.
	 */
	private static class DefaultRestClientGraphQlClient
			extends AbstractDelegatingGraphQlClient implements RestClientGraphQlClient {

		private final RestClient restClient;

		private final Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer;

		DefaultRestClientGraphQlClient(
				GraphQlClient delegate, RestClient restClient,
				Consumer<AbstractGraphQlClientBuilder<?>> builderInitializer) {

			super(delegate);

			Assert.notNull(restClient, "RestClient is required");
			Assert.notNull(builderInitializer, "`builderInitializer` is required");

			this.restClient = restClient;
			this.builderInitializer = builderInitializer;
		}

		public DefaultRestClientGraphQlClientBuilder mutate() {
			DefaultRestClientGraphQlClientBuilder builder = new DefaultRestClientGraphQlClientBuilder(this.restClient);
			this.builderInitializer.accept(builder);
			return builder;
		}
	}

}

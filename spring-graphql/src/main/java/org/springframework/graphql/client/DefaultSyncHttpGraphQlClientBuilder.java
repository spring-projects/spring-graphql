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
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * Default {@link HttpSyncGraphQlClient.Builder} implementation, a simple wrapper
 * around a {@link RestClient.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
final class DefaultSyncHttpGraphQlClientBuilder
		extends AbstractGraphQlClientSyncBuilder<DefaultSyncHttpGraphQlClientBuilder>
		implements HttpSyncGraphQlClient.Builder<DefaultSyncHttpGraphQlClientBuilder>  {

	private final RestClient.Builder restClientBuilder;


	/**
	 * Constructor to start without a RestClient instance.
	 */
	DefaultSyncHttpGraphQlClientBuilder() {
		this(RestClient.builder());
	}

	/**
	 * Constructor to start with a pre-configured {@code RestClient}.
	 */
	DefaultSyncHttpGraphQlClientBuilder(RestClient client) {
		this(client.mutate());
	}

	/**
	 * Constructor to start with a pre-configured {@code RestClient}.
	 */
	DefaultSyncHttpGraphQlClientBuilder(RestClient.Builder clientBuilder) {
		this.restClientBuilder = clientBuilder;
	}


	@Override
	public DefaultSyncHttpGraphQlClientBuilder url(String url) {
		this.restClientBuilder.baseUrl(url);
		return this;
	}

	@Override
	public DefaultSyncHttpGraphQlClientBuilder url(URI url) {
		UriBuilderFactory factory = new DefaultUriBuilderFactory(UriComponentsBuilder.fromUri(url));
		this.restClientBuilder.uriBuilderFactory(factory);
		return this;
	}

	@Override
	public DefaultSyncHttpGraphQlClientBuilder header(String name, String... values) {
		this.restClientBuilder.defaultHeader(name, values);
		return this;
	}

	@Override
	public DefaultSyncHttpGraphQlClientBuilder headers(Consumer<HttpHeaders> headersConsumer) {
		this.restClientBuilder.defaultHeaders(headersConsumer);
		return this;
	}

	@Override
	public DefaultSyncHttpGraphQlClientBuilder messageConverters(Consumer<List<HttpMessageConverter<?>>> configurer) {
		this.restClientBuilder.messageConverters(configurer);
		return this;
	}

	@Override
	public DefaultSyncHttpGraphQlClientBuilder restClient(Consumer<RestClient.Builder> configurer) {
		configurer.accept(this.restClientBuilder);
		return this;
	}

	@Override
	public HttpSyncGraphQlClient build() {

		this.restClientBuilder.messageConverters(converters -> {
			HttpMessageConverter<Object> converter = HttpMessageConverterDelegate.findJsonConverter(converters);
			setJsonConverter(converter);
		});

		RestClient restClient = this.restClientBuilder.build();
		HttpSyncGraphQlTransport transport = new HttpSyncGraphQlTransport(restClient);

		GraphQlClient graphQlClient = super.buildGraphQlClient(transport);
		return new DefaultHttpSyncGraphQlClient(graphQlClient, restClient, getBuilderInitializer());
	}


	/**
	 * Default {@link HttpSyncGraphQlClient} implementation.
	 */
	private static class DefaultHttpSyncGraphQlClient
			extends AbstractDelegatingGraphQlClient implements HttpSyncGraphQlClient {

		private final RestClient restClient;

		private final Consumer<AbstractGraphQlClientSyncBuilder<?>> builderInitializer;

		DefaultHttpSyncGraphQlClient(
				GraphQlClient delegate, RestClient restClient,
				Consumer<AbstractGraphQlClientSyncBuilder<?>> builderInitializer) {

			super(delegate);

			Assert.notNull(restClient, "RestClient is required");
			Assert.notNull(builderInitializer, "`builderInitializer` is required");

			this.restClient = restClient;
			this.builderInitializer = builderInitializer;
		}

		public DefaultSyncHttpGraphQlClientBuilder mutate() {
			DefaultSyncHttpGraphQlClientBuilder builder = new DefaultSyncHttpGraphQlClientBuilder(this.restClient);
			this.builderInitializer.accept(builder);
			return builder;
		}
	}

}

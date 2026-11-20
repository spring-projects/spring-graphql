/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
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
	@SuppressWarnings("removal")
	public DefaultSyncHttpGraphQlClientBuilder messageConverters(Consumer<List<HttpMessageConverter<?>>> configurer) {
		this.restClientBuilder.messageConverters(configurer);
		return this;
	}

	@Override
	public DefaultSyncHttpGraphQlClientBuilder configureMessageConverters(Consumer<HttpMessageConverters.ClientBuilder> configurer) {
		this.restClientBuilder.configureMessageConverters(configurer);
		return this;
	}

	@Override
	public DefaultSyncHttpGraphQlClientBuilder restClient(Consumer<RestClient.Builder> configurer) {
		configurer.accept(this.restClientBuilder);
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public HttpSyncGraphQlClient build() {
		this.restClientBuilder.configureMessageConverters((builder) -> {
			builder.registerDefaults().configureMessageConverters((converter) -> {
				if (HttpMessageConverterDelegate.isJsonConverter(converter)) {
					setJsonConverter((HttpMessageConverter<Object>) converter);
				}
			});
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

		@Override
		public DefaultSyncHttpGraphQlClientBuilder mutate() {
			DefaultSyncHttpGraphQlClientBuilder builder = new DefaultSyncHttpGraphQlClientBuilder(this.restClient);
			this.builderInitializer.accept(builder);
			return builder;
		}
	}

}

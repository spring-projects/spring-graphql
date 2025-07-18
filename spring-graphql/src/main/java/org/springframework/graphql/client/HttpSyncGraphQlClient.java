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
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.client.RestClient;


/**
 * GraphQL over HTTP client with that uses {@link RestClient} in a blocking
 * execution chain.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 * @see SyncGraphQlTransport
 */
public interface HttpSyncGraphQlClient extends GraphQlClient {


	@Override
	Builder<?> mutate();


	/**
	 * Create an {@link HttpSyncGraphQlClient} that uses the given {@link RestClient}.
	 * @param client the {@code RestClient} to use for HTTP requests
	 */
	static HttpSyncGraphQlClient create(RestClient client) {
		return builder(client.mutate()).build();
	}

	/**
	 * Return a builder to initialize an {@link HttpSyncGraphQlClient} with.
	 */
	static Builder<?> builder() {
		return new DefaultSyncHttpGraphQlClientBuilder();
	}

	/**
	 * Variant of {@link #builder()} with a pre-configured {@code RestClient}
	 * to mutate and customize further through the returned builder.
	 * @param client the {@code RestClient} to use for HTTP requests
	 */
	static Builder<?> builder(RestClient client) {
		return builder(client.mutate());
	}

	/**
	 * Variant of {@link #builder()} with a pre-configured {@code RestClient}
	 * to mutate and customize further through the returned builder.
	 * @param builder the {@code RestClient} builder to use for HTTP requests
	 */
	static Builder<?> builder(RestClient.Builder builder) {
		return new DefaultSyncHttpGraphQlClientBuilder(builder);
	}


	/**
	 * Builder for the GraphQL over HTTP client with a blocking execution chain.
	 * @param <B> the type of builder
	 */
	interface Builder<B extends Builder<B>> extends GraphQlClient.SyncBuilder<B> {

		/**
		 * Set the GraphQL endpoint URL as a String.
		 * @param url the url to send HTTP requests to
		 */
		B url(String url);

		/**
		 * Set the GraphQL endpoint URL.
		 * @param url the url to send HTTP requests to
		 */
		B url(URI url);

		/**
		 * Add the given header to HTTP requests.
		 * @param name the header name
		 * @param values the header values
		 */
		B header(String name, String... values);

		/**
		 * Variant of {@link #header(String, String...)} that provides access
		 * to the underlying headers to inspect or modify directly.
		 * @param headersConsumer a function that consumes the {@code HttpHeaders}
		 */
		B headers(Consumer<HttpHeaders> headersConsumer);

		/**
		 * Configure message converters for JSON for use in the
		 * {@link org.springframework.graphql.GraphQlResponse} to convert response
		 * data to higher level objects.
		 * @param configurer the configurer to apply
		 * @return this builder
		 * @deprecated since 2.0 in favor of {@link #configureMessageConverters(Consumer)}.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		B messageConverters(Consumer<List<HttpMessageConverter<?>>> configurer);

		/**
		 * Configure message converters for JSON for use in the
		 * {@link org.springframework.graphql.GraphQlResponse} to convert response
		 * data to higher level objects.
		 * @param configurer the configurer to apply
		 * @return this builder
		 */
		B configureMessageConverters(Consumer<HttpMessageConverters.ClientBuilder> configurer);

		/**
		 * Customize the underlying {@code RestClient}.
		 * <p>Note that some properties of {@code RestClient.Builder} like the base URL,
		 * headers, and message converters can be customized through this builder.
		 * @param builderConsumer a consumer that customizes the {@code RestClient}.
		 * @see #url(String)
		 * @see #header(String, String...)
		 * @see #configureMessageConverters(Consumer)
		 */
		B restClient(Consumer<RestClient.Builder> builderConsumer);

		/**
		 * Build the {@code HttpSyncGraphQlClient} instance.
		 */
		@Override
		HttpSyncGraphQlClient build();

	}

}

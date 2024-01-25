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
import org.springframework.web.client.RestClient;


/**
 * GraphQL over HTTP client that uses {@link RestClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.3
 */
public interface HttpSyncGraphQlClient extends GraphQlClient {


	@Override
	Builder<?> mutate();


	/**
	 * Create an {@link HttpSyncGraphQlClient} that uses the given {@link RestClient}.
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
	 */
	static Builder<?> builder(RestClient client) {
		return builder(client.mutate());
	}

	/**
	 * Variant of {@link #builder()} with a pre-configured {@code RestClient}
	 * to mutate and customize further through the returned builder.
	 */
	static Builder<?> builder(RestClient.Builder builder) {
		return new DefaultSyncHttpGraphQlClientBuilder(builder);
	}


	/**
	 * Builder for the GraphQL over HTTP client.
	 */
	interface Builder<B extends Builder<B>> extends GraphQlClient.SyncBuilder<B> {

		/**
		 * Set the GraphQL endpoint URL as a String.
		 * @param url the url to send HTTP requests to or connect over WebSocket
		 */
		B url(String url);

		/**
		 * Set the GraphQL endpoint URL.
		 * @param url the url to send HTTP requests to or connect over WebSocket
		 */
		B url(URI url);

		/**
		 * Add the given header to HTTP requests or to the WebSocket handshake request.
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
		 * Configure message converters for all JSON encoding and decoding needs.
		 * @param configurer the configurer to apply
		 * @return this builder
		 */
		B messageConverters(Consumer<List<HttpMessageConverter<?>>> configurer);

		/**
		 * Customize the {@code RestClient} to use.
		 * <p>Note that some properties of {@code RestClient.Builder} like the base URL,
		 * headers, and message converters can be customized through this builder.
		 * @see #url(String)
		 * @see #header(String, String...)
		 * @see #messageConverters(Consumer)
		 */
		B restClient(Consumer<RestClient.Builder> builderConsumer);

		/**
		 * Build the {@code RestClientGraphQlClient} instance.
		 */
		@Override
		HttpSyncGraphQlClient build();

	}

}

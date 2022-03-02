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
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.WebClient;


/**
 * {@code GraphQlClient} for GraphQL over HTTP via {@link WebClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface HttpGraphQlClient extends GraphQlClient {


	@Override
	Builder<?> mutate();


	/**
	 * Create an {@link HttpGraphQlClient} that uses the given {@link WebClient}.
	 */
	static HttpGraphQlClient create(WebClient webClient) {
		return builder(webClient).build();
	}

	/**
	 * Return a builder to initialize an {@link HttpGraphQlClient} with.
	 */
	static Builder<?> builder() {
		return new DefaultHttpGraphQlClient.Builder();
	}

	/**
	 * Variant of {@link #builder()} with a pre-configured {@code WebClient}
	 * which may be mutated and further customized through the returned builder.
	 */
	static Builder<?> builder(WebClient webClient) {
		return new DefaultHttpGraphQlClient.Builder(webClient);
	}


	/**
	 * Base builder for GraphQL clients over a Web transport.
	 */
	interface BaseBuilder<B extends BaseBuilder<B>> extends GraphQlClient.Builder<B> {

		/**
		 * Set the GraphQL endpoint URL.
		 * @param url the url to make requests to
		 */
		B url(@Nullable String url);

		/**
		 * Set the GraphQL endpoint URL.
		 * @param url the url to make requests to
		 */
		B url(@Nullable URI url);

		/**
		 * Add the given header to HTTP requests to the endpoint URL.
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
		 * Provide a {@code Consumer} to customize the {@code ClientCodecConfigurer}
		 * for JSON encoding and decoding of GraphQL payloads.
		 */
		B codecConfigurer(Consumer<ClientCodecConfigurer> codecsConsumer);

	}


	/**
	 * Builder for a GraphQL over HTTP client.
	 */
	interface Builder<B extends Builder<B>> extends BaseBuilder<B> {

		/**
		 * Customize the {@code WebClient} to use.
		 */
		B webClient(Consumer<WebClient.Builder> webClient);

		/**
		 * Build the {@code HttpGraphQlClient}.
		 */
		@Override
		HttpGraphQlClient build();

	}

}

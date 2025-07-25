/*
 * Copyright 2002-present the original author or authors.
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


/**
 * Base contract for the HTTP and WebSocket {@code GraphQlClient} extensions.
 * Defines a builder with common configuration for both transports.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebGraphQlClient extends GraphQlClient {


	@Override
	Builder<?> mutate();


	/**
	 * Base builder for GraphQL clients over a Web transport.
	 * @param <B> the type of builder
	 */
	interface Builder<B extends Builder<B>> extends GraphQlClient.Builder<B> {

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
		 * Configure JSON encoders and decoders for use in the
		 * {@link org.springframework.graphql.GraphQlResponse} to convert response
		 * data to higher level objects.
		 * @param codecsConsumer a callback that customizes the configured codecs
		 */
		B codecConfigurer(Consumer<CodecConfigurer> codecsConsumer);

		/**
		 * Build a {@code WebGraphQlClient} instance.
		 */
		@Override
		WebGraphQlClient build();

	}

}

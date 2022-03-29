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

import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;

/**
 * Server-side tester, without a client, that executes requests through a
 * {@link WebGraphQlHandler}. Similar to {@link ExecutionGraphQlServiceTester} but also
 * adding a web processing layer with a {@code WebGraphQlInterceptor}
 * chain.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see HttpGraphQlTester
 * @see WebSocketGraphQlTester
 */
public interface WebGraphQlTester extends GraphQlTester {


	@Override
	Builder<?> mutate();


	/**
	 * Create a {@link WebGraphQlTester} instance.
	 */
	static WebGraphQlTester create(WebGraphQlHandler graphQlHandler) {
		return builder(graphQlHandler).build();
	}

	/**
	 * Return a builder for a {@link WebGraphQlTester}.
	 * @param graphQlHandler the handler to execute requests
	 */
	static WebGraphQlTester.Builder<?> builder(WebGraphQlHandler graphQlHandler) {
		return new DefaultWebGraphQlTesterBuilder(graphQlHandler);
	}


	/**
	 * Common builder for Web {@code GraphQlTester} extensions.
	 */
	interface Builder<B extends Builder<B>> extends GraphQlTester.Builder<B> {

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
		 * Configure the underlying {@code CodecConfigurer} to use for all JSON
		 * encoding and decoding needs.
		 */
		B codecConfigurer(Consumer<CodecConfigurer> codecsConsumer);

		/**
		 * Build a {@link WebGraphQlTester} instance.
		 */
		@Override
		WebGraphQlTester build();

	}

}

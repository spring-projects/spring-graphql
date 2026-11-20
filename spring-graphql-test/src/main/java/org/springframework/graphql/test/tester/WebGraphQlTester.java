/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
	 * @param graphQlHandler the web GraphQL handler to be tested
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
	 * @param <B> the type of builder
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
		 * @param codecsConsumer a consumer that customizes the configured codecs
		 */
		B codecConfigurer(Consumer<CodecConfigurer> codecsConsumer);

		/**
		 * Build a {@link WebGraphQlTester} instance.
		 */
		@Override
		WebGraphQlTester build();

	}

}

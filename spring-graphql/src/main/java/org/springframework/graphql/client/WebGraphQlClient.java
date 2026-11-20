/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

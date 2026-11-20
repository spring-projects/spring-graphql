/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import java.util.function.Consumer;

import org.springframework.web.reactive.function.client.WebClient;


/**
 * GraphQL over HTTP client that uses {@link WebClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface HttpGraphQlClient extends WebGraphQlClient {


	@Override
	Builder<?> mutate();


	/**
	 * Create an {@link HttpGraphQlClient} that uses the given {@link WebClient}.
	 * @param webClient the {@code WebClient} to use for sending HTTP requests
	 */
	static HttpGraphQlClient create(WebClient webClient) {
		return builder(webClient.mutate()).build();
	}

	/**
	 * Return a builder to initialize an {@link HttpGraphQlClient} with.
	 */
	static Builder<?> builder() {
		return new DefaultHttpGraphQlClientBuilder();
	}

	/**
	 * Variant of {@link #builder()} with a pre-configured {@code WebClient}
	 * to mutate and customize further through the returned builder.
	 * @param webClient the {@code WebClient} to use for sending HTTP requests
	 */
	static Builder<?> builder(WebClient webClient) {
		return builder(webClient.mutate());
	}

	/**
	 * Variant of {@link #builder()} with a pre-configured {@code WebClient}
	 * to mutate and customize further through the returned builder.
	 * @param webClientBuilder the {@code WebClient.Builder} to use for building the HTTP client
	 */
	static Builder<?> builder(WebClient.Builder webClientBuilder) {
		return new DefaultHttpGraphQlClientBuilder(webClientBuilder);
	}


	/**
	 * Builder for the GraphQL over HTTP client.
	 * @param <B> the builder type
	 */
	interface Builder<B extends Builder<B>> extends WebGraphQlClient.Builder<B> {

		/**
		 * Customize the {@code WebClient} to use.
		 * <p>Note that some properties of {@code WebClient.Builder} like the
		 * base URL, headers, and codecs can be customized through this builder.
		 * @param webClient the function for customizing the {@code WebClient.Builder} that's used to build the HTTP client
		 * @see #url(String)
		 * @see #header(String, String...)
		 * @see #codecConfigurer(Consumer)
		 */
		B webClient(Consumer<WebClient.Builder> webClient);

		/**
		 * Build the {@code HttpGraphQlClient} instance.
		 */
		@Override
		HttpGraphQlClient build();

	}

}

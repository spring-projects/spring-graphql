/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;

import java.util.function.Consumer;

import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * GraphQL over HTTP tester that uses {@link WebTestClient} and supports tests
 * with or without a running server, depending on how {@code WebTestClient} is
 * configured.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface HttpGraphQlTester extends WebGraphQlTester {


	@Override
	Builder<?> mutate();


	/**
	 * Create an {@link HttpGraphQlTester} that uses the given {@link WebTestClient}.
	 * @param webTestClient the {@code WebTestClient} to use
	 */
	static HttpGraphQlTester create(WebTestClient webTestClient) {
		return builder(webTestClient.mutate()).build();
	}

	/**
	 * Return a builder to initialize an {@link HttpGraphQlTester} by creating
	 * the underlying {@link WebTestClient} through the given builder.
	 * @param webTestClientBuilder the {@code WebTestClient} builder to use
	 */
	static HttpGraphQlTester.Builder<?> builder(WebTestClient.Builder webTestClientBuilder) {
		return new DefaultHttpGraphQlTesterBuilder(webTestClientBuilder);
	}


	/**
	 * Builder for the GraphQL over HTTP tester.
	 * @param <B> the type of builder
	 */
	interface Builder<B extends Builder<B>> extends WebGraphQlTester.Builder<B> {

		/**
		 * Customize the {@code WebTestClient} to use.
		 * <p>Note that some properties of {@code WebTestClient.Builder} like the
		 * base URL, headers, and codecs can be customized through this builder.
		 * @param webClient a consumer that customizes the {@code WebClient} builder
		 * @see #url(String)
		 * @see #header(String, String...)
		 * @see #codecConfigurer(Consumer)
		 */
		B webTestClient(Consumer<WebTestClient.Builder> webClient);

		/**
		 * Build the {@code HttpGraphQlTester} instance.
		 */
		@Override
		HttpGraphQlTester build();

	}

}

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

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
	 */
	static Builder<?> builder(WebClient webClient) {
		return builder(webClient.mutate());
	}

	/**
	 * Variant of {@link #builder()} with a pre-configured {@code WebClient}
	 * to mutate and customize further through the returned builder.
	 */
	static Builder<?> builder(WebClient.Builder webClientBuilder) {
		return new DefaultHttpGraphQlClientBuilder(webClientBuilder);
	}


	/**
	 * Builder for the GraphQL over HTTP client.
	 */
	interface Builder<B extends Builder<B>> extends WebGraphQlClient.Builder<B> {

		/**
		 * Customize the {@code WebClient} to use.
		 * <p>Note that some properties of {@code WebClient.Builder} like the
		 * base URL, headers, and codecs can be customized through this builder.
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

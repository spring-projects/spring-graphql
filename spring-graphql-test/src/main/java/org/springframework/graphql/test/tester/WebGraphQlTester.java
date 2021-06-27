/*
 * Copyright 2002-2021 the original author or authors.
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

import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Main entry point for testing GraphQL over a Web transport with requests
 * executed {@link #create(WebTestClient) via WebTestClient} or
 * {@link #create(WebGraphQlHandler) via WebGraphQlHandler}. See the below for
 * examples with different scenarios.
 *
 * <p>
 * GraphQL requests to Spring MVC without an HTTP server: <pre class="code">
 * &#064;SpringBootTest
 * &#064;AutoConfigureMockMvc
 * &#064;AutoConfigureGraphQlTester
 * public class MyTests {
 *
 *   &#064;Autowired
 *   private WebGraphQlTester graphQlTester;
 * }
 * </pre>
 *
 * <p>
 * GraphQL requests to Spring WebFlux without an HTTP server: <pre class="code">
 * &#064;SpringBootTest
 * &#064;AutoConfigureWebTestClient
 * &#064;AutoConfigureGraphQlTester
 * public class MyTests {
 *
 *   &#064;Autowired
 *   private WebGraphQlTester graphQlTester;
 * }
 * </pre>
 *
 * <p>
 * GraphQL requests to a running server: <pre class="code">
 * &#064;SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
 * &#064;AutoConfigureWebTestClient
 * &#064;AutoConfigureGraphQlTester
 * public class MyTests {
 *
 *   &#064;Autowired
 *   private WebGraphQlTester graphQlTester;
 * }
 * </pre>
 *
 * <p>
 * GraphQL requests handled directly through a {@link WebGraphQlHandler}: <pre class="code">
 * &#064;SpringBootTest
 * public class MyTests {
 *
 *   private WebGraphQlTester graphQlTester;
 *
 *   &#064;BeforeEach
 *   public void setUp(&#064;Autowired WebGraphQLHandler handler) {
 *       this.graphQlTester = GraphQlTester.create(handler);
 *   }
 * }
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebGraphQlTester extends GraphQlTester {

	/**
	 * {@inheritDoc}
	 * <p>The returned spec for Web request input also allows adding HTTP headers.
	 */
	WebRequestSpec query(String query);


	/**
	 * Create a {@code WebGraphQlTester} that performs GraphQL requests as an
	 * HTTP client through the given {@link WebTestClient}. Depending on how the
	 * {@code WebTestClient} is set up, tests may be with or without a server.
	 * See setup examples in class-level Javadoc.
	 * @param client the web client to perform requests with
	 * @return the created {@code WebGraphQlTester}
	 */
	static WebGraphQlTester create(WebTestClient client) {
		return builder(client).build();
	}

	/**
	 * Create a {@code WebGraphQlTester} that performs GraphQL requests through
	 * the given {@link WebGraphQlHandler}.
	 * @param handler the handler to execute requests with
	 * @return the created {@code WebGraphQlTester}
	 */
	static WebGraphQlTester create(WebGraphQlHandler handler) {
		return builder(handler).build();
	}

	/**
	 * Return a builder with options to initialize a {@code WebGraphQlTester}.
	 * @param client the client to execute requests with
	 * @return the builder to use
	 */
	static Builder builder(WebTestClient client) {
		return new DefaultWebGraphQlTester.DefaultBuilder(client);
	}

	/**
	 * Return a builder with options to initialize a {@code WebGraphQlHandler}.
	 * @param handler the handler to execute requests with
	 * @return the builder to use
	 */
	static Builder builder(WebGraphQlHandler handler) {
		return new DefaultWebGraphQlTester.DefaultBuilder(handler);
	}


	/**
	 * A builder to create a {@link WebGraphQlTester} instance.
	 */
	interface Builder extends GraphQlTester.Builder<Builder> {

		/**
		 * Add the given header to all requests that haven't added it.
		 * @param headerName the header name
		 * @param headerValues the header values
		 */
		Builder defaultHeader(String headerName, String... headerValues);

		/**
		 * Variant of {@link #defaultHeader(String, String...)} that provides
		 * access to the underlying headers to inspect or modify directly.
		 * @param headersConsumer a function that consumes the {@code HttpHeaders}
		 */
		Builder defaultHeaders(Consumer<HttpHeaders> headersConsumer);

		/**
		 * Build the {@code WebGraphQlTester}.
		 * @return the created instance
		 */
		@Override
		WebGraphQlTester build();

	}

	/**
	 * Extends {@link GraphQlTester.RequestSpec} with further input options
	 * applicable to Web requests.
	 */
	interface WebRequestSpec extends RequestSpec<WebRequestSpec> {

		/**
		 * Add the given, single header value under the given name.
		 * @param headerName  the header name
		 * @param headerValues the header value(s)
		 * @return the same instance
		 */
		WebRequestSpec header(String headerName, String... headerValues);

		/**
		 * Manipulate the request's headers with the given consumer. The
		 * headers provided to the consumer are "live", so that the consumer can
		 * be used to {@linkplain HttpHeaders#set(String, String) overwrite}
		 * existing header values, {@linkplain HttpHeaders#remove(Object) remove}
		 * values, or use any of the other {@link HttpHeaders} methods.
		 * @param headersConsumer a function that consumes the {@code HttpHeaders}
		 * @return this builder
		 */
		WebRequestSpec headers(Consumer<HttpHeaders> headersConsumer);

	}

}

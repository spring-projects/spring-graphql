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

import org.springframework.graphql.web.WebGraphQlHandler;
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
	 * Create a {@code WebGraphQlTester} that performs GraphQL requests as an
	 * HTTP client through the given {@link WebTestClient}. Depending on how the
	 * {@code WebTestClient} is set up, tests may be with or without a server.
	 * See setup examples in class-level Javadoc.
	 * @param client the web client to perform requests with
	 * @return the created {@code WebGraphQlTester}
	 */
	static WebGraphQlTester create(WebTestClient client) {
		return new DefaultWebGraphQlTester(client);
	}

	/**
	 * Create a {@code WebGraphQlTester} that performs GraphQL requests through
	 * the given {@link WebGraphQlHandler}.
	 * @param handler the handler to execute requests with
	 * @return the created {@code WebGraphQlTester}
	 */
	static WebGraphQlTester create(WebGraphQlHandler handler) {
		return new DefaultWebGraphQlTester(handler);
	}

}

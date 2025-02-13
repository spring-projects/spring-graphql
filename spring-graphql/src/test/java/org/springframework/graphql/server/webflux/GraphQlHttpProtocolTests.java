/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.server.webflux;


import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebGraphQlSetup;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Tests for {@link GraphQlHttpHandler} that check whether it supports
 * the GraphQL over HTTP specification.
 *
 * @see <a href="https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json">GraphQL over HTTP specification</a>
 */
public class GraphQlHttpProtocolTests {

	private GraphQlSetup greetingSetup = GraphQlSetup.schemaContent("type Query { greeting: String }")
			.queryFetcher("greeting", (env) -> "Hello");

	/*
	 * If the GraphQL response contains the data entry, and it is not null,
	 * then the server MUST reply with a 2xx status code and SHOULD reply with 200 status code.
	 */
	@Test
	void successWhenValidRequest() {
		WebTestClient testClient = createTestClient(greetingSetup);
		WebTestClient.ResponseSpec response = postGraphQlRequest(testClient, "{ greeting }");
		response.expectStatus().isOk()
				.expectHeader().contentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.expectBody()
				.jsonPath("$.data.greeting").isEqualTo("Hello")
				.jsonPath("$.errors").doesNotExist();
	}

	/*
	 * If the GraphQL response contains the data entry and it is not null,
	 * then the server MUST reply with a 2xx status code and SHOULD reply with 200 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Field-errors-encountered-during-execution
	 */
	@Test
	void partialSuccessWhenError() {
		GraphQlSetup graphQlSetup = GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", (env) -> BookSource.getBookWithoutAuthor(1L))
				.dataFetcher("Book", "author", (env) -> {
					throw new IllegalStateException("custom error");
				});
		WebTestClient testClient = createTestClient(graphQlSetup);
		WebTestClient.ResponseSpec response = postGraphQlRequest(testClient, "{ bookById(id: 1) { id author { firstName } } }");
		response.expectStatus().isOk()
				.expectHeader().contentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.expectBody()
				.jsonPath("$.data.bookById.id").isEqualTo("1")
				.jsonPath("$.data.bookById.author").isEmpty()
				.jsonPath("$.errors[*].extensions.classification").isEqualTo("INTERNAL_ERROR");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.JSON-parsing-failure
	 */
	@Test
	void requestErrorWhenJsonParsingFailure() {
		WebTestClient testClient = createTestClient(greetingSetup);
		testClient.post().uri("/graphql")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.bodyValue("NONSENSE")
				.exchange().expectStatus().isBadRequest()
				.expectHeader().doesNotExist("Content-Type")
				.expectBody().isEmpty();
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Invalid-parameters
	 */
	@Test
	void requestErrorWhenInvalidParameters() {
		WebTestClient testClient = createTestClient(greetingSetup);
		testClient.post().uri("/graphql")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.bodyValue("{\"qeury\": \"{__typename}\"}")
				.exchange().expectStatus().isBadRequest()
				.expectHeader().doesNotExist("Content-Type")
				.expectBody().isEmpty();
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Document-parsing-failure
	 */
	@Test
	void requestErrorWhenDocumentParsingFailure() {
		WebTestClient testClient = createTestClient(greetingSetup);
		WebTestClient.ResponseSpec response = postGraphQlRequest(testClient, "{");
		response.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.expectBody()
				.jsonPath("$.data").doesNotExist()
				.jsonPath("$.errors[*].extensions.classification").isEqualTo("InvalidSyntax");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Document-validation-failure
	 */
	@Test
	void requestErrorWhenInvalidDocument() {
		WebTestClient testClient = createTestClient(greetingSetup);
		WebTestClient.ResponseSpec response = postGraphQlRequest(testClient, "{ unknown }");
		response.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.expectBody()
				.jsonPath("$.data").doesNotExist()
				.jsonPath("$.errors[*].extensions.classification").isEqualTo("ValidationError");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Operation-cannot-be-determined
	 */
	@Test
	void requestErrorWhenUndeterminedOperation() {
		WebTestClient testClient = createTestClient(greetingSetup);
		String document = """
				{
				"query" : "{ greeting }",
				"operationName" : "unknown"
				}
				""";
		testClient.post().uri("/graphql")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.bodyValue(document)
				.exchange().expectStatus().isBadRequest()
				.expectHeader().contentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.expectBody()
				.jsonPath("$.data").doesNotExist()
				.jsonPath("$.errors[*].extensions.classification").isEqualTo("ValidationError");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Variable-coercion-failure
	 */
	@Test
	void requestErrorWhenVariableCoercion() {
		GraphQlSetup graphQlSetup = GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", (env) -> BookSource.getBookWithoutAuthor(1L));
		WebTestClient testClient = createTestClient(graphQlSetup);
		WebTestClient.ResponseSpec response = postGraphQlRequest(testClient, "{ bookById(id: false) { id } }");
		response.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.expectBody().jsonPath("$.data").doesNotExist()
				.jsonPath("$.errors[*].extensions.classification").isEqualTo("ValidationError");
	}

	/*
	 * If the GraphQL response contains the data entry and it is null, then the server SHOULD reply
	 * with a 2xx status code and it is RECOMMENDED it replies with 200 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json
	 */
	@Test
	void successWhenEmptyData() {
		WebGraphQlSetup graphQlSetup = GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", (env) -> null)
				.interceptor(new WebGraphQlInterceptor() {
					@Override
					public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
						return chain.next(request).map(response ->
								response.transform(builder -> builder.data(null).build()));
					}
				});
		WebTestClient testClient = createTestClient(graphQlSetup);
		WebTestClient.ResponseSpec response = postGraphQlRequest(testClient, "{ bookById(id: 100) { id } }");
		response.expectStatus().isOk()
				.expectHeader().contentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.expectBody().jsonPath("$.data").isEmpty();
	}

	WebTestClient.ResponseSpec postGraphQlRequest(WebTestClient testClient, String query) {
		String document = "{ \"query\" : \"" + query + "\" }";
		return testClient.post().uri("/graphql")
				.accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(document)
				.exchange();
	}

	static WebTestClient createTestClient(WebGraphQlSetup graphQlSetup) {
		GenericWebApplicationContext context = new GenericWebApplicationContext();
		AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(context);
		reader.register(WebFluxTestConfig.class);
		GraphQlHttpHandler httpHandler = graphQlSetup.toHttpHandlerWebFlux();
		RouterFunction<ServerResponse> routerFunction = RouterFunctions
				.route()
				.POST("/graphql", RequestPredicates.accept(MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE),
						httpHandler::handleRequest).build();
		context.registerBean(RouterFunction.class, () -> routerFunction);
		context.refresh();
		return WebTestClient.bindToRouterFunction(routerFunction).build();
	}

	@Configuration
	@EnableWebFlux
	static class WebFluxTestConfig implements WebFluxConfigurer {

	}
}

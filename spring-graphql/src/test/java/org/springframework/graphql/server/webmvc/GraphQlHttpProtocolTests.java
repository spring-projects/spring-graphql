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

package org.springframework.graphql.server.webmvc;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

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
		MockMvcTester mvcTester = createMvcTester(greetingSetup);
		MvcTestResultAssert resultAssert = postGraphQlRequest(mvcTester, "{ greeting }");
		resultAssert.hasStatusOk()
				.hasContentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.bodyJson().extractingPath("$.data.greeting").isEqualTo("Hello");
		resultAssert.bodyJson().doesNotHavePath("$.errors");
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
		MockMvcTester mvcTester = createMvcTester(graphQlSetup);
		MvcTestResultAssert resultAssert = postGraphQlRequest(mvcTester, "{ bookById(id: 1) { id author { firstName } } }");
		resultAssert.hasStatusOk().hasContentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
		resultAssert.bodyJson().extractingPath("$.data.bookById.id").isEqualTo("1");
		resultAssert.bodyJson().extractingPath("$.data.bookById.author").isNull();
		resultAssert.bodyJson().extractingPath("$.errors[*].extensions.classification").asArray().contains("INTERNAL_ERROR");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.JSON-parsing-failure
	 */
	@Test
	void requestErrorWhenJsonParsingFailure() {
		MockMvcTester mvcTester = createMvcTester(greetingSetup);
		assertThat(mvcTester.post().accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE).contentType(MediaType.APPLICATION_JSON)
				.uri("/graphql")
				.content("NONSENSE"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.contentType().isNull();
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Invalid-parameters
	 */
	@Test
	void requestErrorWhenInvalidParameters() {
		MockMvcTester mvcTester = createMvcTester(greetingSetup);
		mvcTester.post().accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE).contentType(MediaType.APPLICATION_JSON)
				.uri("/graphql")
				.content("{\"qeury\": \"{__typename}\"}")
				.assertThat()
				.hasStatus(HttpStatus.BAD_REQUEST)
				.contentType().isNull();
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Document-parsing-failure
	 */
	@Test
	void requestErrorWhenDocumentParsingFailure() {
		MockMvcTester mvcTester = createMvcTester(greetingSetup);
		MvcTestResultAssert resultAssert = postGraphQlRequest(mvcTester, "{");
		resultAssert.bodyJson().doesNotHavePath("$.data");
		resultAssert.bodyJson().extractingPath("$.errors[*].extensions.classification").asArray().contains("InvalidSyntax");
		resultAssert.hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Document-validation-failure
	 */
	@Test
	void requestErrorWhenInvalidDocument() {
		MockMvcTester mvcTester = createMvcTester(greetingSetup);
		MvcTestResultAssert resultAssert = postGraphQlRequest(mvcTester, "{ unknown }");
		resultAssert.bodyJson().doesNotHavePath("$.data");
		resultAssert.bodyJson().extractingPath("$.errors[*].extensions.classification").asArray().contains("ValidationError");
		resultAssert.hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Operation-cannot-be-determined
	 */
	@Test
	void requestErrorWhenUndeterminedOperation() {
		MockMvcTester mvcTester = createMvcTester(greetingSetup);
		String document = """
				{
				"query" : "{ greeting }",
				"operationName" : "unknown"
				}
				""";
		MvcTestResultAssert resultAssert = mvcTester.post().accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE).contentType(MediaType.APPLICATION_JSON)
				.uri("/graphql")
				.content(document)
				.assertThat();
		resultAssert.hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
		resultAssert.bodyJson().doesNotHavePath("$.data");
		resultAssert.bodyJson().extractingPath("$.errors[*].extensions.classification").asArray().contains("ValidationError");
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
		MockMvcTester mvcTester = createMvcTester(graphQlSetup);
		MvcTestResultAssert resultAssert = postGraphQlRequest(mvcTester, "{ bookById(id: false) { id } }");
		resultAssert.hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
		resultAssert.bodyJson().doesNotHavePath("$.data");
		resultAssert.bodyJson().extractingPath("$.errors[*].extensions.classification").asArray().contains("ValidationError");
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
		MockMvcTester mvcTester = createMvcTester(graphQlSetup);
		MvcTestResultAssert resultAssert = postGraphQlRequest(mvcTester, "{ bookById(id: 100) { id } }");
		resultAssert
				.hasStatusOk()
				.hasContentType(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.bodyJson().extractingPath("$.data").isNull();
	}


	MvcTestResultAssert postGraphQlRequest(MockMvcTester mvcTester, String query) {
		String document = "{ \"query\" : \"" + query + "\" }";
		return mvcTester.post().accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE).contentType(MediaType.APPLICATION_JSON)
				.uri("/graphql")
				.content(document).assertThat();
	}

	static MockMvcTester createMvcTester(WebGraphQlSetup graphQlSetup) {
		GenericWebApplicationContext context = new GenericWebApplicationContext();
		AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(context);
		reader.register(MvcTestConfig.class);
		context.setServletContext(new MockServletContext());
		GraphQlHttpHandler httpHandler = graphQlSetup.toHttpHandler();
		RouterFunction<ServerResponse> routerFunction = RouterFunctions
				.route()
				.POST("/graphql", RequestPredicates.accept(MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE),
						httpHandler::handleRequest).build();
		context.registerBean(RouterFunction.class, () -> routerFunction);
		context.refresh();
		return MockMvcTester.create(MockMvcBuilders.routerFunctions(routerFunction).build());
	}

	@Configuration
	@EnableWebMvc
	static class MvcTestConfig implements WebMvcConfigurer {

	}

}

/*
 * Copyright 2020-present the original author or authors.
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

import java.io.IOException;
import java.util.stream.Stream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for {@link GraphQlClient} that check whether it supports
 * the GraphQL over HTTP specification.
 *
 * @see <a href="https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json">GraphQL over HTTP specification</a>
 */
class HttpGraphQlClientProtocolTests {

	private MockWebServer server;

	@BeforeEach
	void setup() throws IOException {
		this.server = new MockWebServer();
		this.server.start();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.server.shutdown();
	}

	private static Stream<Arguments> graphQlClientTypes() {
		return Stream.of(
				arguments(named("HttpGraphQlClient", ClientType.ASYNC)),
				arguments(named("HttpSyncGraphQlClient", ClientType.SYNC))
		);
	}

	/*
	 * If the GraphQL response contains the data entry, and it is not null,
	 * then the server MUST reply with a 2xx status code and SHOULD reply with 200 status code.
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void successWhenValidRequest(ClientType clientType) {
		prepareOkResponse("""
				{
					"data": {
						"greeting": "Hello World!"
					}
				}
				""");
		ClientGraphQlResponse response = createClient(clientType).document("{ greeting }")
				.executeSync();
		assertThat(response.isValid()).isTrue();
		assertThat(response.getErrors()).isEmpty();
		assertThat(response.field("greeting").toEntity(String.class)).isEqualTo("Hello World!");
	}

	/*
	 * If the GraphQL response contains the data entry and it is not null,
	 * then the server MUST reply with a 2xx status code and SHOULD reply with 200 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Field-errors-encountered-during-execution
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void partialSuccessWhenError(ClientType clientType) {
		prepareOkResponse("""
				{
					"errors":[
						{
							"message":"INTERNAL_ERROR for 4ed1a08d-e0ee-0e6b-bc66-4bb61f2e3edb",
							"locations":[{"line":1,"column":24}],
							"path":["bookById","author"],
							"extensions":{"classification":"INTERNAL_ERROR"}
						}
					],
					"data":{"bookById":{"id":"1","author":null}}
				}
				""");

		ClientGraphQlResponse response = createClient(clientType)
				.document("""
						{
							bookById(id: 1) {
								id
								author {
									firstName
								}
							}
						}
						""")
				.executeSync();

		assertThat(response.isValid()).isTrue();
		assertThat(response.getErrors()).singleElement()
				.extracting("message").asString().contains("INTERNAL_ERROR");
		assertThat(response.field("bookById.id").toEntity(Long.class)).isEqualTo(1L);
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.JSON-parsing-failure
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void requestErrorWhenInvalidRequest(ClientType clientType) {
		prepareBadRequestResponse();
		assertThatThrownBy(() -> createClient(clientType).document("{ greeting }")
				.executeSync()).isInstanceOfAny(HttpClientErrorException.class, WebClientResponseException.class);
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Document-parsing-failure
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void requestErrorWhenDocumentParsingFailure(ClientType clientType) {
		prepareBadRequestResponse("""
					{
						"errors":[
							{
								"message":"Invalid syntax with offending token '<EOF>' at line 1 column 2",
								"locations":[{"line":1,"column":2}],
								"extensions":{"classification":"InvalidSyntax"}
							}
						]
					}
				""");
		ClientGraphQlResponse response = createClient(clientType).document("{")
				.executeSync();
		assertThat(response.isValid()).isFalse();
		assertThat(response.getErrors()).singleElement()
				.extracting("message").asString().contains("Invalid syntax with offending token");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Document-validation-failure
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void requestErrorWhenInvalidDocument(ClientType clientType) {
		prepareBadRequestResponse("""
				{
					"errors":[
						{
						"message":"Validation error (FieldUndefined@[unknown]) : Field 'unknown' in type 'Query' is undefined",
						"locations":[{"line":1,"column":3}],
						"extensions":{"classification":"ValidationError"}}
					]
				}
				""");
		ClientGraphQlResponse response = createClient(clientType).document("{ unknown }")
				.executeSync();
		assertThat(response.isValid()).isFalse();
		assertThat(response.getErrors()).singleElement()
				.extracting("message").asString().contains("Validation error");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Operation-cannot-be-determined
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void requestErrorWhenUndeterminedOperation(ClientType clientType) {
		prepareBadRequestResponse("""
				{
					"errors":[
						{
							"message":"Unknown operation named 'unknown'.",
							"extensions":{"classification":"ValidationError"}
						}
					]
				}
				""");
		ClientGraphQlResponse response = createClient(clientType).document("""
				{
				"query" : "{ greeting }",
				"operationName" : "unknown"
				}
				""").executeSync();
		assertThat(response.isValid()).isFalse();
		assertThat(response.getErrors()).singleElement()
				.extracting("message").asString().contains("Unknown operation named 'unknown'.");
	}

	/*
	 * If the request is not a well-formed GraphQL-over-HTTP request, or it does not pass validation,
	 * then the server SHOULD reply with 400 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json.Examples.Variable-coercion-failure
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void requestErrorWhenVariableCoercion(ClientType clientType) {
		prepareBadRequestResponse("""
				{
					"errors":[
						{
							"message":"Validation error (WrongType@[bookById]) : argument 'id' with value 'BooleanValue{value=false}' is not a valid 'ID' - Expected an AST type of 'IntValue' or 'StringValue' but it was a 'BooleanValue'",
							"locations":[{"line":1,"column":12}],
							"extensions":{"classification":"ValidationError"}
						}
					]
				}
				""");
		ClientGraphQlResponse response = createClient(clientType).document("{ bookById(id: false) { id } }").executeSync();
		assertThat(response.isValid()).isFalse();
		assertThat(response.getErrors()).singleElement()
				.extracting("message").asString().contains("Validation error (WrongType@[bookById])");
	}

	/*
	 * If the GraphQL response contains the data entry and it is null, then the server SHOULD reply
	 * with a 2xx status code and it is RECOMMENDED it replies with 200 status code.
	 * https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json
	 */
	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void successWhenEmptyData(ClientType clientType) {
		prepareOkResponse("{\"data\":null}");
		ClientGraphQlResponse response = createClient(clientType).document("{ bookById(id: 100) { id } }").executeSync();
		assertThat(response.isValid()).isFalse();
		assertThat(response.getErrors()).isEmpty();
	}

	private GraphQlClient createClient(ClientType type) {
		return switch (type) {
			case ASYNC -> HttpGraphQlClient.builder().url(server.url("/").toString()).build();
			case SYNC -> HttpSyncGraphQlClient.builder().url(server.url("/").toString()).build();
		};
	}

	private void prepareOkResponse(String body) {
		prepareResponse(200, body);
	}

	private void prepareBadRequestResponse(String body) {
		prepareResponse(400, body);
	}

	private void prepareBadRequestResponse() {
		MockResponse mockResponse = new MockResponse();
		mockResponse.setResponseCode(400);
		this.server.enqueue(mockResponse);
	}

	private void prepareResponse(int status, String body) {
		MockResponse mockResponse = new MockResponse();
		mockResponse.setResponseCode(status);
		mockResponse.setHeader("Content-Type", "application/graphql-response+json");
		mockResponse.setBody(body);
		this.server.enqueue(mockResponse);
	}

	enum ClientType {
		ASYNC, SYNC
	}

}

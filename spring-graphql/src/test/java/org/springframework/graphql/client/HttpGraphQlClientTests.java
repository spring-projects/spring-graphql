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
import java.util.List;
import java.util.stream.Stream;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.graphql.Book;
import org.springframework.graphql.MediaTypes;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Integration tests for {@link HttpGraphQlClient}.
 */
class HttpGraphQlClientTests {

	private MockWebServer server;

	@BeforeEach
	void setup() throws IOException {
		this.server = new MockWebServer();
		this.server.start();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.server.close();
	}

	static Stream<Arguments> graphQlClientTypes() {
		return Stream.of(
				arguments(named("HttpGraphQlClient", ClientType.ASYNC)),
				arguments(named("HttpSyncGraphQlClient", ClientType.SYNC))
		);
	}

	@ParameterizedTest
	@MethodSource("graphQlClientTypes")
	void toEntityListSync(ClientType clientType) {
		prepareOkResponse("""
				{
					"data": {
						"favoriteBooks": [
							{"id":"42","name":"Hitchhiker's Guide to the Galaxy"},
							{"id":"53","name":"Breaking Bad"}
						]
					}
				}
				""");
		List<Book> favoriteBooks = createClient(clientType).document("{ favoriteBooks() { id name } }")
				.retrieveSync("favoriteBooks")
				.toEntityList(Book.class);

		assertThat(favoriteBooks).hasSize(2);
		assertThat(favoriteBooks.get(0)).isInstanceOf(Book.class);
		assertThat(favoriteBooks).extracting("id").contains(42L, 53L);
	}

	private GraphQlClient createClient(ClientType clientType) {
		return switch (clientType) {
			case ASYNC -> HttpGraphQlClient.builder().url(server.url("/").toString()).build();
			case SYNC -> HttpSyncGraphQlClient.builder().url(server.url("/").toString()).build();
		};
	}

	void prepareOkResponse(String body) {
		prepareResponse(200, MediaTypes.APPLICATION_GRAPHQL_RESPONSE, body);
	}

	private void prepareResponse(int status, MediaType contentType, String body) {
		MockResponse mockResponse = new MockResponse.Builder().code(status)
				.setHeader("Content-Type", contentType.toString())
				.body(body)
				.build();
		this.server.enqueue(mockResponse);
	}

	enum ClientType {
		ASYNC, SYNC
	}

}

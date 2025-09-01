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


import java.util.Map;
import java.util.stream.Stream;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.graphql.Book;
import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.MockWebServerExtension;
import org.springframework.graphql.client.json.GraphQlJacksonModule;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HttpGraphQlTransport}.
 *
 * @author Brian Clozel
 */
@ExtendWith(MockWebServerExtension.class)
class HttpGraphQlTransportIntegrationTests {

	@ParameterizedTest
	@MethodSource("argumentValues")
	void shouldSerializeFieldValues(ProjectInput projectInput, String variable, MockWebServer server) throws Exception {
		JsonMapper jsonMapper = JsonMapper.builder().addModule(new GraphQlJacksonModule()).build();
		JacksonJsonEncoder jsonEncoder = new JacksonJsonEncoder(jsonMapper, MediaType.APPLICATION_JSON);
		WebClient webClient = WebClient.builder()
				.baseUrl(server.url("/graphql").toString())
				.codecs(codecs -> codecs.defaultCodecs().jacksonJsonEncoder(jsonEncoder))
				.build();
		HttpGraphQlClient graphQlClient = HttpGraphQlClient.create(webClient);

		server.enqueue(new MockResponse.Builder().addHeader("Content-Type", MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.body("""
						{
							"data": {
								"createProject": {
									"id": "spring-graphql"
								}
							}
						}
						""")
				.build());
		graphQlClient.document("""
						mutation createProject($project: ProjectInput!) {
							  createProject($project: $project) {
								id
							  }
						}
						""")
				.variables(Map.of("project", projectInput))
				.executeSync();

		assertThat(server.takeRequest().getBody().utf8()).contains("\"variables\":{\"project\":" + variable + "}");
	}

	static Stream<Arguments> argumentValues() {
		return Stream.of(
				Arguments.arguments(new ProjectInput("spring-graphql", ArgumentValue.omitted()), "{\"id\":\"spring-graphql\"}"),
				Arguments.arguments(new ProjectInput("spring-graphql", ArgumentValue.ofNullable(null)), "{\"id\":\"spring-graphql\",\"name\":null}"),
				Arguments.arguments(new ProjectInput("spring-graphql", ArgumentValue.ofNullable("Spring for GraphQL")), "{\"id\":\"spring-graphql\",\"name\":\"Spring for GraphQL\"}")
		);
	}


	@Test
	void shouldStreamSubscriptionResultsOverSse(MockWebServer server) {
		WebClient webClient = WebClient.create(server.url("/graphql").toString());
		HttpGraphQlClient graphQlClient = HttpGraphQlClient.create(webClient);
		Flux<ClientGraphQlResponse> responses = graphQlClient
				.document("subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } ")
				.executeSubscription();

		server.enqueue(new MockResponse.Builder().addHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.body("""
						event:next
						data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

						event:next
						data:{"data":{"bookSearch":{"id":"5","name":"Animal Farm"}}}

						event:complete

						""")
				.build());

		StepVerifier.create(responses)
				.assertNext(item -> assertThat(item.field("bookSearch").toEntity(Book.class).getName()).isEqualTo("Nineteen Eighty-Four"))
				.assertNext(item -> assertThat(item.field("bookSearch").toEntity(Book.class).getName()).isEqualTo("Animal Farm"))
				.verifyComplete();
	}

	@Test
	void shouldStreamSubscriptionErrorsOverSse(MockWebServer server) {
		WebClient webClient = WebClient.create(server.url("/graphql").toString());
		HttpGraphQlClient graphQlClient = HttpGraphQlClient.create(webClient);
		Flux<ClientGraphQlResponse> responses = graphQlClient
				.document("subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } ")
				.executeSubscription();

		server.enqueue(new MockResponse.Builder().addHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.body("""
						event:next
						data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

						event:next
						data:{"errors":[{"message":"Subscription error","locations":[],"extensions":{"classification":"INTERNAL_ERROR"}}]}

						event:complete

						""")
				.build());
		StepVerifier.create(responses)
				.assertNext(item -> assertThat(item.field("bookSearch").toEntity(Book.class).getName()).isEqualTo("Nineteen Eighty-Four"))
				.assertNext(item -> {
					assertThat(item.getErrors()).hasSize(1);
					assertThat(item.getErrors().get(0).getErrorType().toString()).isEqualTo("INTERNAL_ERROR");
					assertThat(item.getErrors().get(0).getMessage()).isEqualTo("Subscription error");
				})
				.verifyComplete();
	}


	public record ProjectInput(String id, ArgumentValue<String> name) {

	}

}

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


import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.graphql.Book;
import org.springframework.graphql.MockWebServerExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HttpGraphQlTransport}.
 *
 * @author Brian Clozel
 */
@ExtendWith(MockWebServerExtension.class)
class HttpGraphQlTransportIntegrationTests {

	@Test
	void shouldStreamSubscriptionResultsOverSse(MockWebServer server) {
		WebClient webClient = WebClient.create(server.url("/graphql").toString());
		HttpGraphQlClient graphQlClient = HttpGraphQlClient.create(webClient);
		Flux<ClientGraphQlResponse> responses = graphQlClient
				.document("subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } ")
				.executeSubscription();

		server.enqueue(new MockResponse().addHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						event:next
						data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

						event:next
						data:{"data":{"bookSearch":{"id":"5","name":"Animal Farm"}}}

						event:complete

						"""));

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

		server.enqueue(new MockResponse().addHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
				.setBody("""
						event:next
						data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

						event:next
						data:{"errors":[{"message":"Subscription error","locations":[],"extensions":{"classification":"INTERNAL_ERROR"}}]}

						event:complete

						"""));
		StepVerifier.create(responses)
				.assertNext(item -> assertThat(item.field("bookSearch").toEntity(Book.class).getName()).isEqualTo("Nineteen Eighty-Four"))
				.assertNext(item -> {
					assertThat(item.getErrors()).hasSize(1);
					assertThat(item.getErrors().get(0).getErrorType().toString()).isEqualTo("INTERNAL_ERROR");
					assertThat(item.getErrors().get(0).getMessage()).isEqualTo("Subscription error");
				})
				.verifyComplete();
	}


}

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


import java.time.Duration;
import java.util.Collections;
import java.util.List;

import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlSseHandler}.
 *
 * @author Brian Clozel
 */
class GraphQlSseHandlerTests {

	private static final List<HttpMessageWriter<?>> MESSAGE_WRITERS =
			List.of(new ServerSentEventHttpMessageWriter(new Jackson2JsonEncoder()));

	private static final DataFetcher<?> SEARCH_DATA_FETCHER = env -> {
		String author = env.getArgument("author");
		return Flux.fromIterable(BookSource.books())
				.filter((book) -> book.getAuthor().getFullName().contains(author));
	};

	private final MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/graphql")
			.contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).build();



	@Test
	void shouldRejectQueryOperations() {
		SerializableGraphQlRequest request = initRequest("{ bookById(id: 42) {name} }");
		GraphQlSseHandler handler = createSseHandler(SEARCH_DATA_FETCHER);
		MockServerHttpResponse response = handleRequest(this.httpRequest, handler, request);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data:{"errors":[{"message":"SSE handler supports only subscriptions","locations":[],"extensions":{"classification":"OperationNotSupported"}}]}

				event:complete
				data:{}

				""");
	}

	@Test
	void shouldWriteMultipleEventsForSubscription() {

		SerializableGraphQlRequest request = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		GraphQlSseHandler handler = createSseHandler(SEARCH_DATA_FETCHER);
		MockServerHttpResponse response = handleRequest(this.httpRequest, handler, request);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:next
				data:{"data":{"bookSearch":{"id":"5","name":"Animal Farm"}}}

				event:complete
				data:{}

				""");
	}

	@Test // gh-1213
	void shouldHandleNonPublisherValue() {

		SerializableGraphQlRequest request = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		GraphQlSseHandler handler = createSseHandler(env -> BookSource.getBook(1L));
		MockServerHttpResponse response = handleRequest(this.httpRequest, handler, request);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:complete
				data:{}

				""");
	}

	@Test
	void shouldWriteEventsAndTerminalError() {

		SerializableGraphQlRequest request = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		DataFetcher<?> errorDataFetcher = env ->
				Flux.just(BookSource.getBook(1L)).concatWith(Flux.error(new IllegalStateException("test error")));

		GraphQlSseHandler handler = createSseHandler(errorDataFetcher);
		MockServerHttpResponse response = handleRequest(this.httpRequest, handler, request);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:next
				data:{"errors":[{"message":"Subscription error","locations":[],"extensions":{"classification":"INTERNAL_ERROR"}}]}

				event:complete
				data:{}

				""");
	}

	@Test
	void shouldSendKeepAlivePings() {
		SerializableGraphQlRequest request = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		WebGraphQlHandler webGraphQlHandler = createWebGraphQlHandler(env -> Mono.delay(Duration.ofMillis(50)).then());
		GraphQlSseHandler handler = new GraphQlSseHandler(webGraphQlHandler, null, Duration.ofMillis(10));

		assertThat(handleRequest(this.httpRequest, handler, request).getBodyAsString().block())
				.startsWith("""
					:

					:

					""")
				.endsWith("""
					:

					event:complete
					data:{}

					""");
	}

	private GraphQlSseHandler createSseHandler(DataFetcher<?> subscriptionDataFetcher) {
		WebGraphQlHandler webGraphQlHandler = createWebGraphQlHandler(subscriptionDataFetcher);
		return new GraphQlSseHandler(webGraphQlHandler);
	}

	private static WebGraphQlHandler createWebGraphQlHandler(DataFetcher<?> subscriptionDataFetcher) {
		return GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", (env) -> BookSource.getBookWithoutAuthor(1L))
				.subscriptionFetcher("bookSearch", subscriptionDataFetcher)
				.toWebGraphQlHandler();
	}

	private static SerializableGraphQlRequest initRequest(String document) {
		SerializableGraphQlRequest request = new SerializableGraphQlRequest();
		request.setQuery(document);
		return request;
	}

	private MockServerHttpResponse handleRequest(
			MockServerHttpRequest request, GraphQlSseHandler handler, GraphQlRequest body) {

		MockServerWebExchange exchange = MockServerWebExchange.from(request);

		MockServerRequest serverRequest = MockServerRequest.builder()
				.exchange(exchange)
				.uri(exchange.getRequest().getURI())
				.method(exchange.getRequest().getMethod())
				.headers(exchange.getRequest().getHeaders())
				.body(Mono.just(body));

		handler.handleRequest(serverRequest)
				.flatMap(response -> response.writeTo(exchange, new DefaultContext()))
				.block();

		return exchange.getResponse();
	}


	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return MESSAGE_WRITERS;
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return Collections.emptyList();
		}

	}

}

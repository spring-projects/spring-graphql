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

package org.springframework.graphql.server.webflux;


import java.time.Duration;
import java.util.Collections;
import java.util.List;

import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
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
			List.of(new ServerSentEventHttpMessageWriter(new JacksonJsonEncoder()));

	private static final List<HttpMessageReader<?>> MESSAGE_READERS =
			List.of(new DecoderHttpMessageReader<>(new JacksonJsonDecoder()));

	private static final DataFetcher<?> SEARCH_DATA_FETCHER = env -> {
		String author = env.getArgument("author");
		return Flux.fromIterable(BookSource.books())
				.filter((book) -> book.getAuthor().getFullName().contains(author));
	};


	@Test
	void shouldRejectQueryOperations() throws Exception {
		MockServerHttpRequest httpRequest = initRequest("{ bookById(id: 42) {name} }");
		GraphQlSseHandler handler = createSseHandler(SEARCH_DATA_FETCHER);
		MockServerHttpResponse response = handleRequest(httpRequest, handler);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data: {"errors":[{"message":"Operation type 'QUERY' is not allowed for this request","extensions":{"classification":"OperationNotSupported"}}]}

				event:complete
				data: {}

				""");
	}

	@Test
	void shouldWriteMultipleEventsForSubscription() throws Exception {

		MockServerHttpRequest httpRequest = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		GraphQlSseHandler handler = createSseHandler(SEARCH_DATA_FETCHER);
		MockServerHttpResponse response = handleRequest(httpRequest, handler);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data: {"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:next
				data: {"data":{"bookSearch":{"id":"5","name":"Animal Farm"}}}

				event:complete
				data: {}

				""");
	}

	@Test // gh-1213
	void shouldHandleNonPublisherValue() throws Exception {

		MockServerHttpRequest httpRequest = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		GraphQlSseHandler handler = createSseHandler(env -> BookSource.getBook(1L));
		MockServerHttpResponse response = handleRequest(httpRequest, handler);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data: {"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:complete
				data: {}

				""");
	}

	@Test
	void shouldWriteEventsAndTerminalError() throws Exception {

		MockServerHttpRequest httpRequest = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		DataFetcher<?> errorDataFetcher = env ->
				Flux.just(BookSource.getBook(1L)).concatWith(Flux.error(new IllegalStateException("test error")));

		GraphQlSseHandler handler = createSseHandler(errorDataFetcher);
		MockServerHttpResponse response = handleRequest(httpRequest, handler);

		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
		assertThat(response.getBodyAsString().block()).isEqualTo("""
				event:next
				data: {"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:next
				data: {"errors":[{"message":"Subscription error","locations":[],"extensions":{"classification":"INTERNAL_ERROR"}}]}

				event:complete
				data: {}

				""");
	}

	@Test
	void shouldSendKeepAlivePings() throws Exception {
		MockServerHttpRequest httpRequest = initRequest(
				"subscription TestSubscription { bookSearch(author:\"Orwell\") { id name } }");

		WebGraphQlHandler webGraphQlHandler = createWebGraphQlHandler(env -> Mono.delay(Duration.ofMillis(50)).then());
		GraphQlSseHandler handler = GraphQlSseHandler.builder(webGraphQlHandler).keepAliveDuration(Duration.ofMillis(10)).build();

		assertThat(handleRequest(httpRequest, handler).getBodyAsString().block())
				.startsWith("""
					:

					:

					""")
				.endsWith("""
					:

					event:complete
					data: {}

					""");
	}

	private GraphQlSseHandler createSseHandler(DataFetcher<?> subscriptionDataFetcher) {
		WebGraphQlHandler webGraphQlHandler = createWebGraphQlHandler(subscriptionDataFetcher);
		return GraphQlSseHandler.builder(webGraphQlHandler).build();
	}

	private static WebGraphQlHandler createWebGraphQlHandler(DataFetcher<?> subscriptionDataFetcher) {
		return GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", (env) -> BookSource.getBookWithoutAuthor(1L))
				.subscriptionFetcher("bookSearch", subscriptionDataFetcher)
				.toWebGraphQlHandler();
	}

	private static MockServerHttpRequest initRequest(String document) throws Exception {
		SerializableGraphQlRequest request = new SerializableGraphQlRequest();
		request.setQuery(document);
		String json = new ObjectMapper().writeValueAsString(request);
		return MockServerHttpRequest.post("/graphql")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
				.body(json);
	}

	private MockServerHttpResponse handleRequest(MockServerHttpRequest request, GraphQlSseHandler handler) {
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		ServerRequest serverRequest = ServerRequest.create(exchange, MESSAGE_READERS);

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

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

package org.springframework.graphql.server.webmvc;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import graphql.schema.DataFetcher;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link GraphQlSseHandler}.
 *
 * @author Brian Clozel
 */
@SuppressWarnings("ReactiveStreamsUnusedPublisher")
class GraphQlSseHandlerTests {

	private static final List<HttpMessageConverter<?>> MESSAGE_READERS =
			List.of(new JacksonJsonHttpMessageConverter());

	private static final AtomicBoolean DATA_FETCHER_CANCELLED = new AtomicBoolean();

	private static final DataFetcher<?> SEARCH_DATA_FETCHER = env -> {
		String author = env.getArgument("author");
		return Flux.fromIterable(BookSource.books())
				.filter((book) -> book.getAuthor().getFullName().contains(author))
				.doOnCancel(() -> DATA_FETCHER_CANCELLED.set(true));
	};

	private static final String BOOK_SEARCH_REQUEST = """
			{ "query": "subscription TestSubscription { bookSearch(author:\\"Orwell\\") { id name } }" }
			""";


	@Test
	void shouldRejectQueryOperations() throws Exception {
		GraphQlSseHandler handler = createSseHandler(SEARCH_DATA_FETCHER);
		MockHttpServletRequest request = createServletRequest("{ \"query\": \"{ bookById(id: 42) {name} }\"}");
		MockHttpServletResponse response = handleAndAwait(request, handler);

		assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
		assertThat(response.getContentAsString()).isEqualTo("""
				event:next
				data:{"errors":[{"message":"SSE handler supports only subscriptions","locations":[],"extensions":{"classification":"OperationNotSupported"}}]}

				event:complete
				data:

				""");
	}

	@Test
	void shouldWriteMultipleEventsForSubscription() throws Exception {
		GraphQlSseHandler handler = createSseHandler(SEARCH_DATA_FETCHER);
		MockHttpServletRequest request = createServletRequest(BOOK_SEARCH_REQUEST);
		MockHttpServletResponse response = handleAndAwait(request, handler);

		assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
		assertThat(response.getContentAsString()).isEqualTo("""
				event:next
				data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:next
				data:{"data":{"bookSearch":{"id":"5","name":"Animal Farm"}}}

				event:complete
				data:

				""");
	}

	@Test // gh-1213
	void shouldHandleNonPublisherValue() throws Exception {
		GraphQlSseHandler handler = createSseHandler(env -> BookSource.getBook(1L));
		MockHttpServletRequest request = createServletRequest("""
				{ "query": "subscription TestSubscription { bookSearch { id name } }" }
				""");
		MockHttpServletResponse response = handleAndAwait(request, handler);

		assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
		assertThat(response.getContentAsString()).isEqualTo("""
				event:next
				data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:complete
				data:

				""");
	}

	@Test
	void shouldSendKeepAlivePings() throws Exception {
		WebGraphQlHandler webGraphQlHandler = createWebGraphQlHandler(env -> Mono.delay(Duration.ofMillis(50)).then());
		GraphQlSseHandler handler = new GraphQlSseHandler(webGraphQlHandler, null, Duration.ofMillis(10));

		MockHttpServletRequest request = createServletRequest(BOOK_SEARCH_REQUEST);
		MockHttpServletResponse response = handleRequest(request, handler);
		await().atMost(Duration.ofSeconds(1)).until(() -> response.getContentAsString().contains("complete"));

		assertThat(response.getContentAsString())
				.startsWith("""
					:\s

					:\s

					""")
				.endsWith("""
					:\s

					event:complete
					data:

					""");
	}

	@Test
	void shouldWriteEventsAndTerminalError() throws Exception {

		DataFetcher<?> errorDataFetcher = env -> Flux.just(BookSource.getBook(1L))
				.concatWith(Flux.error(new IllegalStateException("test error")));

		GraphQlSseHandler handler = createSseHandler(errorDataFetcher);
		MockHttpServletRequest request = createServletRequest(BOOK_SEARCH_REQUEST);
		MockHttpServletResponse response = handleAndAwait(request, handler);

		assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
		assertThat(response.getContentAsString()).isEqualTo("""
				event:next
				data:{"data":{"bookSearch":{"id":"1","name":"Nineteen Eighty-Four"}}}

				event:next
				data:{"errors":[{"message":"Subscription error","locations":[],"extensions":{"classification":"INTERNAL_ERROR"}}]}

				event:complete
				data:

				""");
	}

	@Test
	void shouldCancelDataFetcherPublisherWhenWritingFails() throws Exception {
		GraphQlSseHandler handler = createSseHandler(SEARCH_DATA_FETCHER);
		MockHttpServletRequest servletRequest = createServletRequest(BOOK_SEARCH_REQUEST);
		HttpServletResponse servletResponse = mock(HttpServletResponse.class);
		ServletOutputStream outputStream = mock(ServletOutputStream.class);

		willThrow(new IOException("broken pipe")).given(outputStream).write(any(byte[].class));
		willThrow(new IOException("broken pipe")).given(outputStream).write(any(ByteBuffer.class));
		given(servletResponse.getOutputStream()).willReturn(outputStream);

		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = handler.handleRequest(request);
		if (response instanceof AsyncServerResponse asyncResponse) {
			asyncResponse.block();
		}

		response.writeTo(servletRequest, servletResponse, new DefaultContext());
		await().atMost(Duration.ofMillis(500)).until(DATA_FETCHER_CANCELLED::get);
	}

	@Test
	void shouldCancelDataFetcherWhenAsyncTimeout() throws Exception {
		DataFetcher<?> errorDataFetcher = env -> Flux.just(BookSource.getBook(1L))
				.delayElements(Duration.ofMillis(500)).doOnCancel(() -> DATA_FETCHER_CANCELLED.set(true));

		GraphQlSseHandler handler = createSseHandler(errorDataFetcher);
		MockHttpServletRequest servletRequest = createServletRequest(BOOK_SEARCH_REQUEST);

		MockHttpServletResponse servletResponse = handleRequest(servletRequest, handler);
		for (AsyncListener listener : ((MockAsyncContext) servletRequest.getAsyncContext()).getListeners()) {
			listener.onTimeout(new AsyncEvent(servletRequest.getAsyncContext()));
		}

		assertThat(DATA_FETCHER_CANCELLED.get()).isTrue();
		assertThat(servletResponse.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
		assertThat(servletResponse.getContentAsString()).isEmpty();
	}

	private GraphQlSseHandler createSseHandler(DataFetcher<?> dataFetcher) {
		return new GraphQlSseHandler(createWebGraphQlHandler(dataFetcher));
	}

	private static WebGraphQlHandler createWebGraphQlHandler(DataFetcher<?> dataFetcher) {
		return GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", (env) -> BookSource.getBookWithoutAuthor(1L))
				.subscriptionFetcher("bookSearch", dataFetcher)
				.toWebGraphQlHandler();
	}

	private MockHttpServletRequest createServletRequest(String query) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
		request.setContentType(MediaType.APPLICATION_JSON_VALUE);
		request.setContent(query.getBytes(StandardCharsets.UTF_8));
		request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
		request.setAsyncSupported(true);
		return request;
	}

	private MockHttpServletResponse handleRequest(
			MockHttpServletRequest servletRequest, GraphQlSseHandler handler) throws ServletException, IOException {
		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = handler.handleRequest(request);
		if (response instanceof AsyncServerResponse asyncResponse) {
			asyncResponse.block();
		}
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		response.writeTo(servletRequest, servletResponse, new DefaultContext());
		return servletResponse;
	}

	private MockHttpServletResponse handleAndAwait(
			MockHttpServletRequest servletRequest, GraphQlSseHandler handler) throws ServletException, IOException {
		MockHttpServletResponse servletResponse = handleRequest(servletRequest, handler);
		await().atMost(Duration.ofMillis(500)).until(() -> servletResponse.getContentAsString().contains("complete"));
		return servletResponse;
	}


	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageConverter<?>> messageConverters() {
			return MESSAGE_READERS;
		}

	}

}

/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.graphql.web.webmvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.web.ConsumeOneAndNeverCompleteInterceptor;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.graphql.web.WebSocketHandlerTestSupport;
import org.springframework.graphql.web.WebSocketInterceptor;
import org.springframework.graphql.web.webflux.GraphQlWebSocketMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GraphQlWebSocketHandler}.
 */
public class GraphQlWebSocketHandlerTests extends WebSocketHandlerTestSupport {

	private static final HttpMessageConverter<?> converter = new MappingJackson2HttpMessageConverter();

	private final TestWebSocketSession session = new TestWebSocketSession();

	private final GraphQlWebSocketHandler handler = initWebSocketHandler();

	@Test
	void query() throws Exception {
		handle(this.handler,
				new TextMessage("{\"type\":\"connection_init\"}"),
				new TextMessage(BOOK_QUERY));

		StepVerifier.create(this.session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> {
					GraphQlWebSocketMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.getType()).isEqualTo("next");
					assertThat(actual.<Map<String, Object>>getPayload())
							.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.extractingByKey("bookById", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.containsEntry("name", "Nineteen Eighty-Four");
				})
				.consumeNextWith((message) -> assertMessageType(message, "complete"))
				.then(this.session::close) // Complete output Flux
				.verifyComplete();
	}

	@Test
	void subscription() throws Exception {
		handle(this.handler, new TextMessage("{\"type\":\"connection_init\"}"), new TextMessage(BOOK_SUBSCRIPTION));

		BiConsumer<WebSocketMessage<?>, String> bookPayloadAssertion = (message, bookId) -> {
			GraphQlWebSocketMessage actual = decode(message);
			assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
			assertThat(actual.getType()).isEqualTo("next");
			assertThat(actual.<Map<String, Object>>getPayload())
					.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
					.extractingByKey("bookSearch", as(InstanceOfAssertFactories.map(String.class, Object.class)))
					.containsEntry("id", bookId);
		};

		StepVerifier.create(this.session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "1"))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "5"))
				.consumeNextWith((message) -> assertMessageType(message, "complete"))
				.then(this.session::close)// Complete output Flux
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutMessageType() throws Exception {
		handle(this.handler,
				new TextMessage("{\"type\":\"connection_init\"}"),
				new TextMessage("{\"id\":\"" + SUBSCRIPTION_ID + "\"}"));
		// No message type

		StepVerifier.create(this.session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		assertThat(this.session.getCloseStatus()).isEqualTo(new CloseStatus(4400, "Invalid message"));
	}

	@Test
	void invalidMessageWithoutId() throws Exception {
		handle(this.handler,
				new TextMessage("{\"type\":\"connection_init\"}"),
				new TextMessage("{\"type\":\"subscribe\"}")); // No message id

		StepVerifier.create(this.session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		assertThat(this.session.getCloseStatus()).isEqualTo(new CloseStatus(4400, "Invalid message"));
	}

	@Test
	void connectionInitHandling() throws Exception {

		WebSocketInterceptor interceptor = new WebSocketInterceptor() {

			@Override
			public Mono<Object> handleConnectionInitialization(String sessionId, Map<String, Object> payload) {
				Object value = payload.get("key");
				return Mono.just(Collections.singletonMap("key", value + " acknowledged"));
			}
		};

		handle(initWebSocketHandler(interceptor),
				new TextMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}"));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((webSocketMessage) -> {
					GraphQlWebSocketMessage message = decode(webSocketMessage);
					assertThat(message.getType()).isEqualTo("connection_ack");
					assertThat(message.<Map<String, Object>>getPayload()).containsEntry("key", "A acknowledged");
				})
				.then(this.session::close) // Complete output Flux
				.verifyComplete();
	}

	@Test
	void connectionClosedHandling() throws Exception {

		CloseStatus closeStatus = CloseStatus.PROTOCOL_ERROR;
		AtomicBoolean called = new AtomicBoolean();

		WebSocketInterceptor interceptor = new WebSocketInterceptor() {

			@Override
			public void handleConnectionClosed(String sessionId, int status, Map<String, Object> payload) {
				called.set(true);
				assertThat(sessionId).isEqualTo("1");
				assertThat(status).isEqualTo(closeStatus.getCode());
				assertThat(payload).hasSize(1).containsEntry("key", "A");
			}
		};

		GraphQlWebSocketHandler handler = initWebSocketHandler(interceptor);
		handle(handler, new TextMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}"));

		StepVerifier.create(session.getOutput())
				.expectNextCount(1)
				.then(this.session::close) // Complete output Flux
				.verifyComplete();

		handler.afterConnectionClosed(this.session, closeStatus);
		assertThat(called).isTrue();
	}

	@Test
	void connectionInitRejected() throws Exception {

		WebSocketInterceptor interceptor = new WebSocketInterceptor() {

			@Override
			public Mono<Object> handleConnectionInitialization(String sessionId, Map<String, Object> payload) {
				return Mono.error(new IllegalStateException());
			}
		};

		handle(initWebSocketHandler(interceptor), new TextMessage("{\"type\":\"connection_init\"}"));

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4401, "Unauthorized"))
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutConnectionInit() throws Exception {
		handle(this.handler, new TextMessage(BOOK_SUBSCRIPTION));

		StepVerifier.create(this.session.getOutput()).verifyComplete();
		assertThat(this.session.getCloseStatus()).isEqualTo(new CloseStatus(4401, "Unauthorized"));
	}

	@Test
	void tooManyConnectionInitRequests() throws Exception {
		handle(this.handler,
				new TextMessage("{\"type\":\"connection_init\"}"),
				new TextMessage("{\"type\":\"connection_init\"}"));

		StepVerifier.create(this.session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		assertThat(this.session.getCloseStatus()).isEqualTo(new CloseStatus(4429, "Too many initialisation requests"));
	}

	@Test
	void connectionInitTimeout() {
		GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(initHandler(), converter, Duration.ofMillis(50));
		handler.afterConnectionEstablished(this.session);

		StepVerifier.create(this.session.closeStatus())
				.expectNext(new CloseStatus(4408, "Connection initialisation timeout"))
				.verifyComplete();
	}

	@Test
	void subscriptionExists() throws Exception {
		handle(initWebSocketHandler(new ConsumeOneAndNeverCompleteInterceptor()),
				new TextMessage("{\"type\":\"connection_init\"}"),
				new TextMessage(BOOK_SUBSCRIPTION),
				new TextMessage(BOOK_SUBSCRIPTION));

		// Collect messages until session closed
		List<GraphQlWebSocketMessage> messages = new ArrayList<>();
		this.session.getOutput().subscribe((message) -> messages.add(decode(message)));

		StepVerifier.create(this.session.closeStatus())
				.expectNext(new CloseStatus(4409, "Subscriber for " + SUBSCRIPTION_ID + " already exists"))
				.verifyComplete();

		assertThat(messages.size()).isEqualTo(2);
		assertThat(messages.get(0).getType()).isEqualTo("connection_ack");
		assertThat(messages.get(1).getType()).isEqualTo("next");
	}

	@Test
	void clientCompletion() throws Exception {
		GraphQlWebSocketHandler handler = initWebSocketHandler(new ConsumeOneAndNeverCompleteInterceptor());

		handle(handler,
				new TextMessage("{\"type\":\"connection_init\"}"),
				new TextMessage(BOOK_SUBSCRIPTION));

		String completeMessage = "{\"id\":\"" + SUBSCRIPTION_ID + "\",\"type\":\"complete\"}";
		Consumer<String> messageSender = (body) -> {
			try {
				handler.handleTextMessage(this.session, new TextMessage(body));
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		};

		StepVerifier.create(this.session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> assertMessageType(message, "next"))
				.then(() -> messageSender.accept(completeMessage))
				.as("Second subscription with same id is possible only if the first was properly removed")
				.then(() -> messageSender.accept(BOOK_SUBSCRIPTION))
				.consumeNextWith((message) -> assertMessageType(message, "next"))
				.then(() -> messageSender.accept(completeMessage))
				.verifyTimeout(Duration.ofMillis(500));
	}

	@Test
	void errorMessagePayloadIsCorrectArray() throws Exception {
		final String GREETING_QUERY = "{" +
				"\"id\":\"" + SUBSCRIPTION_ID + "\"," +
				"\"type\":\"subscribe\"," +
				"\"payload\":{\"query\": \"" +
				"  subscription TestTypenameSubscription {" +
				"    greeting" +
				"  }\"}" +
				"}";

		String schema = "type Subscription { greeting: String! }type Query { greetingUnused: String! }";

		WebGraphQlHandler initHandler = GraphQlSetup.schemaContent(schema)
				.subscriptionFetcher("greeting", env -> Flux.just("a", null, "b"))
				.webInterceptor()
				.toWebGraphQlHandler();

		GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(initHandler, converter, Duration.ofSeconds(60));

		handle(handler,
				new TextMessage("{\"type\":\"connection_init\"}"),
				new TextMessage(GREETING_QUERY));

		StepVerifier.create(this.session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> {
					GraphQlWebSocketMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.getType()).isEqualTo("next");
					assertThat(actual.<Map<String, Object>>getPayload())
							.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.containsEntry("greeting", "a");
				})
				.consumeNextWith((message) -> {
					GraphQlWebSocketMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.getType()).isEqualTo("error");
					assertThat(actual.<List<Map<String, Object>>>getPayload())
							.asList().hasSize(1)
							.allSatisfy(theError -> assertThat(theError)
									.asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
									.hasSize(3)
									.hasEntrySatisfying("locations", loc -> assertThat(loc).asList().isEmpty())
									.hasEntrySatisfying("message", msg -> assertThat(msg).asString().contains("null"))
									.extractingByKey("extensions", as(InstanceOfAssertFactories.map(String.class, Object.class)))
									.containsEntry("classification", "DataFetchingException"));
				})
				.then(this.session::close)
				.verifyComplete();
	}

	private void handle(GraphQlWebSocketHandler handler, TextMessage... textMessages) throws Exception {
		handler.afterConnectionEstablished(this.session);
		for (TextMessage message : textMessages) {
			handler.handleTextMessage(this.session, message);
		}
	}

	private GraphQlWebSocketHandler initWebSocketHandler(WebInterceptor... interceptors) {
		try {
			return new GraphQlWebSocketHandler(initHandler(interceptors), converter, Duration.ofSeconds(60));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@SuppressWarnings("unchecked")
	private GraphQlWebSocketMessage decode(WebSocketMessage<?> message) {
		try {
			HttpInputMessageAdapter inputMessage = new HttpInputMessageAdapter((TextMessage) message);
			return ((GenericHttpMessageConverter<GraphQlWebSocketMessage>) converter)
					.read(GraphQlWebSocketMessage.class, null, inputMessage);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private void assertMessageType(WebSocketMessage<?> webSocketMessage, String messageType) {
		GraphQlWebSocketMessage message = decode(webSocketMessage);
		assertThat(message.getType()).isEqualTo(messageType);
		if (!messageType.equals("connection_ack")) {
			assertThat(message.getId()).isEqualTo(SUBSCRIPTION_ID);
		}
	}


	private static class HttpInputMessageAdapter extends ByteArrayInputStream implements HttpInputMessage {

		HttpInputMessageAdapter(TextMessage message) {
			super(message.asBytes());
		}

		@Override
		public InputStream getBody() {
			return this;
		}

		@Override
		public HttpHeaders getHeaders() {
			return HttpHeaders.EMPTY;
		}

	}

}

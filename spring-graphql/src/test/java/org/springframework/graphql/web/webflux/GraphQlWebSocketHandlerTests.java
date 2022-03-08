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

package org.springframework.graphql.web.webflux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.web.ConsumeOneAndNeverCompleteInterceptor;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.graphql.web.WebSocketHandlerTestSupport;
import org.springframework.graphql.web.WebSocketInterceptor;
import org.springframework.graphql.web.support.GraphQlMessage;
import org.springframework.graphql.web.support.GraphQlMessageType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GraphQlWebSocketHandler}.
 */
public class GraphQlWebSocketHandlerTests extends WebSocketHandlerTestSupport {

	private static final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();

	@Test
	void query() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_QUERY)));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> {
					GraphQlMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlMessageType.NEXT);
					assertThat(actual.<Map<String, Object>>getPayload())
							.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.extractingByKey("bookById", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.containsEntry("name", "Nineteen Eighty-Four");
				})
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.COMPLETE))
				.verifyComplete();
	}

	@Test
	void subscription() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_SUBSCRIPTION)));

		BiConsumer<WebSocketMessage, String> bookPayloadAssertion = (message, bookId) -> {
			GraphQlMessage actual = decode(message);
			assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
			assertThat(actual.resolvedType()).isEqualTo(GraphQlMessageType.NEXT);
			assertThat(actual.<Map<String, Object>>getPayload())
					.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
					.extractingByKey("bookSearch", as(InstanceOfAssertFactories.map(String.class, Object.class)))
					.containsEntry("id", bookId);
		};

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "1"))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "5"))
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.COMPLETE))
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutMessageType() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"id\":\"" + SUBSCRIPTION_ID + "\", \"payload\":" + BOOK_QUERY_PAYLOAD + "}")));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.CONNECTION_ACK))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.verifyComplete();
	}

	@Test
	void invalidMessageWithoutId() {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"type\":\"subscribe\", \"payload\":{}}")); // No message id

		TestWebSocketSession session = handle(input);

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.CONNECTION_ACK))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.verifyComplete();
	}

	@Test
	void connectionInitHandling() {
		TestWebSocketSession session = handle(
				Flux.just(toWebSocketMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}")),
				new WebSocketInterceptor() {

					@Override
					public Mono<Object> handleConnectionInitialization(String sessionId, Map<String, Object> payload) {
						Object value = payload.get("key");
						return Mono.just(Collections.singletonMap("key", value + " acknowledged"));
					}
				});

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> {
					GraphQlMessage actual = decode(message);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlMessageType.CONNECTION_ACK);
					assertThat(actual.<Map<String, Object>>getPayload()).containsEntry("key", "A acknowledged");
				})
				.verifyComplete();
	}

	@Test
	void connectionClosedHandling() {

		CloseStatus closeStatus = CloseStatus.PROTOCOL_ERROR;
		AtomicBoolean called = new AtomicBoolean();

		TestWebSocketSession session = handle(
				Flux.just(toWebSocketMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}")),
				new WebSocketInterceptor() {

					@Override
					public void handleConnectionClosed(String sessionId, int status, Map<String, Object> payload) {
						called.set(true);
						assertThat(sessionId).isEqualTo("1");
						assertThat(status).isEqualTo(closeStatus.getCode());
						assertThat(payload).hasSize(1).containsEntry("key", "A");
					}
				});

		StepVerifier.create(session.getOutput()).expectNextCount(1).verifyComplete();
		StepVerifier.create(session.close(closeStatus)).verifyComplete();
		assertThat(called).isTrue();
	}

	@Test
	void connectionInitRejected() {
		TestWebSocketSession session = handle(
				Flux.just(toWebSocketMessage("{\"type\":\"connection_init\"}")),
				new WebSocketInterceptor() {

					@Override
					public Mono<Object> handleConnectionInitialization(String sessionId, Map<String, Object> payload) {
						return Mono.error(new IllegalStateException());
					}
				});

		StepVerifier.create(session.getOutput()).verifyComplete();
		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4401, "Unauthorized"))
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutConnectionInit() {
		TestWebSocketSession session = handle(Flux.just(toWebSocketMessage(BOOK_SUBSCRIPTION)));

		StepVerifier.create(session.getOutput()).verifyComplete();
		StepVerifier.create(session.closeStatus()).expectNext(new CloseStatus(4401, "Unauthorized")).verifyComplete();
	}

	@Test
	void tooManyConnectionInitRequests() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"type\":\"connection_init\"}")));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.CONNECTION_ACK))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4429, "Too many initialisation requests"))
				.verifyComplete();
	}

	@Test
	void connectionInitTimeout() {
		GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(
				initHandler(), ServerCodecConfigurer.create(), Duration.ofMillis(50));

		TestWebSocketSession session = new TestWebSocketSession(Flux.empty());
		handler.handle(session).block();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4408, "Connection initialisation timeout"))
				.verifyComplete();
	}

	@Test
	void subscriptionExists() {
		Flux<WebSocketMessage> messageFlux = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_SUBSCRIPTION),
				toWebSocketMessage(BOOK_SUBSCRIPTION));

		TestWebSocketSession session = handle(messageFlux, new ConsumeOneAndNeverCompleteInterceptor());

		// Collect messages until session closed
		List<GraphQlMessage> messages = new ArrayList<>();
		session.getOutput().subscribe((message) -> messages.add(decode(message)));

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4409, "Subscriber for " + SUBSCRIPTION_ID + " already exists"))
				.verifyComplete();

		assertThat(messages.size()).isEqualTo(2);
		assertThat(messages.get(0).resolvedType()).isEqualTo(GraphQlMessageType.CONNECTION_ACK);
		assertThat(messages.get(1).resolvedType()).isEqualTo(GraphQlMessageType.NEXT);
	}

	@Test
	void clientCompletion() {
		Sinks.Many<WebSocketMessage> input = Sinks.many().unicast().onBackpressureBuffer();
		input.tryEmitNext(toWebSocketMessage("{\"type\":\"connection_init\"}"));
		input.tryEmitNext(toWebSocketMessage(BOOK_SUBSCRIPTION));

		TestWebSocketSession session = handle(input.asFlux(), new ConsumeOneAndNeverCompleteInterceptor());

		String completeMessage = "{\"id\":\"" + SUBSCRIPTION_ID + "\",\"type\":\"complete\"}";

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.NEXT))
				.then(() -> input.tryEmitNext(toWebSocketMessage(completeMessage)))
				.as("Second subscription with same id is possible only if the first was properly removed")
				.then(() -> input.tryEmitNext(toWebSocketMessage(BOOK_SUBSCRIPTION)))
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.NEXT))
				.then(() -> input.tryEmitNext(toWebSocketMessage(completeMessage)))
				.verifyTimeout(Duration.ofMillis(500));
	}

	@Test
	void errorMessagePayloadIsArray() {
		final String GREETING_QUERY = "{" +
				"\"id\":\"" + SUBSCRIPTION_ID + "\"," +
				"\"type\":\"subscribe\"," +
				"\"payload\":{\"query\": \"" +
				"  subscription TestTypenameSubscription {" +
				"    greeting" +
				"  }\"}" +
				"}";

		String schema = "type Subscription { greeting: String! } type Query { greetingUnused: String! }";

		WebGraphQlHandler initHandler = GraphQlSetup.schemaContent(schema)
				.subscriptionFetcher("greeting", env -> Flux.just("a", null, "b"))
				.webInterceptor()
				.toWebGraphQlHandler();

		GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(
				initHandler,
				ServerCodecConfigurer.create(),
				Duration.ofSeconds(60));

		TestWebSocketSession session = new TestWebSocketSession(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(GREETING_QUERY)));
		handler.handle(session).block();

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> {
					GraphQlMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlMessageType.NEXT);
					assertThat(actual.<Map<String, Object>>getPayload())
							.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.containsEntry("greeting", "a");
				})
				.consumeNextWith((message) -> {
					GraphQlMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlMessageType.ERROR);
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
				.verifyComplete();
	}

	private TestWebSocketSession handle(Flux<WebSocketMessage> input, WebInterceptor... interceptors) {
		GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(
				initHandler(interceptors),
				ServerCodecConfigurer.create(),
				Duration.ofSeconds(60));

		TestWebSocketSession session = new TestWebSocketSession(input);
		handler.handle(session).block();
		return session;
	}

	private static WebSocketMessage toWebSocketMessage(String data) {
		DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(data.getBytes(StandardCharsets.UTF_8));
		return new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);
	}

	@SuppressWarnings("ConstantConditions")
	private GraphQlMessage decode(WebSocketMessage message) {
		return (GraphQlMessage) decoder.decode(DataBufferUtils.retain(message.getPayload()),
				ResolvableType.forClass(GraphQlMessage.class), null, Collections.emptyMap());
	}

	private void assertMessageType(WebSocketMessage webSocketMessage, GraphQlMessageType messageType) {
		GraphQlMessage message = decode(webSocketMessage);
		assertThat(message.resolvedType()).isEqualTo(messageType);
		if (messageType != GraphQlMessageType.CONNECTION_ACK) {
			assertThat(message.getId()).isEqualTo(SUBSCRIPTION_ID);
		}
	}

}

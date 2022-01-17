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
import java.util.function.BiConsumer;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.web.WebGraphQlHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.web.WebSocketHandlerTestSupport;
import org.springframework.graphql.web.ConsumeOneAndNeverCompleteInterceptor;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.graphql.web.WebSocketInterceptor;
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
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> assertThat(decode(message)).hasSize(3)
						.containsEntry("id", SUBSCRIPTION_ID).containsEntry("type", "next")
						.extractingByKey("payload", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.extractingByKey("bookById", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.containsEntry("name", "Nineteen Eighty-Four"))
				.consumeNextWith((message) -> assertMessageType(message, "complete"))
				.verifyComplete();
	}

	@Test
	void subscription() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_SUBSCRIPTION)));

		BiConsumer<WebSocketMessage, String> bookPayloadAssertion = (message, bookId) ->
				assertThat(decode(message))
						.hasSize(3).containsEntry("id", SUBSCRIPTION_ID).containsEntry("type", "next")
						.extractingByKey("payload", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.extractingByKey("bookSearch", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.containsEntry("id", bookId);

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "1"))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "5"))
				.consumeNextWith((message) -> assertMessageType(message, "complete"))
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutMessageType() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"id\":\"" + SUBSCRIPTION_ID + "\"}")));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.verifyComplete();
	}

	@Test
	void invalidMessageWithoutId() {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"type\":\"subscribe\"}")); // No message id

		TestWebSocketSession session = handle(input);

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void connectionInitHandling() {
		TestWebSocketSession session = handle(
				Flux.just(toWebSocketMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}")),
				new WebSocketInterceptor() {

					@Override
					public Mono<Object> handleConnectionInitialization(Map<String, Object> payload) {
						Object value = payload.get("key");
						return Mono.just(Collections.singletonMap("key", value + " acknowledged"));
					}
				});

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> {
					Map<String, Object> content = decode(message);
					assertThat(content).containsEntry("type", "connection_ack");
					assertThat((Map<String, Object>) content.get("payload")).containsEntry("key", "A acknowledged");
				})
				.verifyComplete();
	}

	@Test
	void connectionInitRejected() {
		TestWebSocketSession session = handle(
				Flux.just(toWebSocketMessage("{\"type\":\"connection_init\"}")),
				new WebSocketInterceptor() {

					@Override
					public Mono<Object> handleConnectionInitialization(Map<String, Object> payload) {
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
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
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
		List<Map<String, Object>> messages = new ArrayList<>();
		session.getOutput().subscribe((message) -> messages.add(decode(message)));

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4409, "Subscriber for " + SUBSCRIPTION_ID + " already exists"))
				.verifyComplete();

		assertThat(messages.size()).isEqualTo(2);
		assertThat(messages.get(0).get("type")).isEqualTo("connection_ack");
		assertThat(messages.get(1).get("type")).isEqualTo("next");
	}

	@Test
	void clientCompletion() {
		Sinks.Many<WebSocketMessage> input = Sinks.many().unicast().onBackpressureBuffer();
		input.tryEmitNext(toWebSocketMessage("{\"type\":\"connection_init\"}"));
		input.tryEmitNext(toWebSocketMessage(BOOK_SUBSCRIPTION));

		TestWebSocketSession session = handle(input.asFlux(), new ConsumeOneAndNeverCompleteInterceptor());

		String completeMessage = "{\"id\":\"" + SUBSCRIPTION_ID + "\",\"type\":\"complete\"}";

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> assertMessageType(message, "next"))
				.then(() -> input.tryEmitNext(toWebSocketMessage(completeMessage)))
				.as("Second subscription with same id is possible only if the first was properly removed")
				.then(() -> input.tryEmitNext(toWebSocketMessage(BOOK_SUBSCRIPTION)))
				.consumeNextWith((message) -> assertMessageType(message, "next"))
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
				.consumeNextWith((message) -> assertMessageType(message, "connection_ack"))
				.consumeNextWith((message) -> assertThat(decode(message))
						.hasSize(3)
						.containsEntry("id", SUBSCRIPTION_ID)
						.containsEntry("type", "next")
						.extractingByKey("payload", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
						.containsEntry("greeting", "a"))
				.consumeNextWith((message) -> assertThat(decode(message))
						.hasSize(3)
						.containsEntry("id", SUBSCRIPTION_ID)
						.containsEntry("type", "error")
						.hasEntrySatisfying("payload", payload -> assertThat(payload)
								.asList()
								.hasSize(1)
								.allSatisfy(theError -> assertThat(theError)
										.asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
										.hasSize(3)
										.hasEntrySatisfying("locations", loc -> assertThat(loc).asList().isEmpty())
										.hasEntrySatisfying("message", msg -> assertThat(msg).asString().contains("null"))
										.extractingByKey("extensions", as(InstanceOfAssertFactories.map(String.class, Object.class)))
										.containsEntry("classification", "DataFetchingException"))))
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

	@SuppressWarnings({ "unchecked", "ConstantConditions" })
	private Map<String, Object> decode(WebSocketMessage message) {
		return (Map<String, Object>) decoder.decode(DataBufferUtils.retain(message.getPayload()),
				GraphQlWebSocketHandler.MAP_RESOLVABLE_TYPE, null, Collections.emptyMap());
	}

	private void assertMessageType(WebSocketMessage message, String messageType) {
		Map<String, Object> map = decode(message);
		assertThat(map).containsEntry("type", messageType);
		if (!messageType.equals("connection_ack")) {
			assertThat(map).containsEntry("id", SUBSCRIPTION_ID);
		}
	}

}

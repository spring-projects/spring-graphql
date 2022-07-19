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

package org.springframework.graphql.server.webflux;

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
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.ConsumeOneAndNeverCompleteInterceptor;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketHandlerTestSupport;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.graphql.server.support.GraphQlWebSocketMessage;
import org.springframework.graphql.server.support.GraphQlWebSocketMessageType;
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

	private static final Duration TIMEOUT = Duration.ofSeconds(5);


	@Test
	void query() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_QUERY)));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> {
					GraphQlWebSocketMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlWebSocketMessageType.NEXT);
					assertThat(actual.<Map<String, Object>>getPayload())
							.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.extractingByKey("bookById", as(InstanceOfAssertFactories.map(String.class, Object.class)))
							.containsEntry("name", "Nineteen Eighty-Four");
				})
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.COMPLETE))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void subscription() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_SUBSCRIPTION)));

		BiConsumer<WebSocketMessage, String> bookPayloadAssertion = (message, bookId) -> {
			GraphQlWebSocketMessage actual = decode(message);
			assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
			assertThat(actual.resolvedType()).isEqualTo(GraphQlWebSocketMessageType.NEXT);
			assertThat(actual.<Map<String, Object>>getPayload())
					.extractingByKey("data", as(InstanceOfAssertFactories.map(String.class, Object.class)))
					.extractingByKey("bookSearch", as(InstanceOfAssertFactories.map(String.class, Object.class)))
					.containsEntry("id", bookId);
		};

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "1"))
				.consumeNextWith((message) -> bookPayloadAssertion.accept(message, "5"))
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.COMPLETE))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void unauthorizedWithoutMessageType() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"id\":\"" + SUBSCRIPTION_ID + "\", \"payload\":" + BOOK_QUERY_PAYLOAD + "}")));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.expectComplete()
				.verify(TIMEOUT);

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void invalidMessageWithoutId() {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"type\":\"subscribe\", \"payload\":{}}")); // No message id

		TestWebSocketSession session = handle(input);

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.expectComplete()
				.verify(TIMEOUT);

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void connectionInitHandling() {
		TestWebSocketSession session = handle(
				Flux.just(toWebSocketMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}")),
				new WebSocketGraphQlInterceptor() {

					@Override
					public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo info, Map<String, Object> payload) {
						Object value = payload.get("key");
						return Mono.just(Collections.singletonMap("key", value + " acknowledged"));
					}
				});

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> {
					GraphQlWebSocketMessage actual = decode(message);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlWebSocketMessageType.CONNECTION_ACK);
					assertThat(actual.<Map<String, Object>>getPayload()).containsEntry("key", "A acknowledged");
				})
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void pingHandling() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}"),
				toWebSocketMessage("{\"type\":\"ping\"}")));

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.consumeNextWith(message -> assertMessageType(message, GraphQlWebSocketMessageType.PONG))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void connectionClosedHandling() {

		CloseStatus closeStatus = CloseStatus.PROTOCOL_ERROR;
		AtomicBoolean called = new AtomicBoolean();

		TestWebSocketSession session = handle(
				Flux.just(toWebSocketMessage("{\"type\":\"connection_init\",\"payload\":{\"key\":\"A\"}}")),
				new WebSocketGraphQlInterceptor() {

					@Override
					public void handleConnectionClosed(WebSocketSessionInfo info, int status, Map<String, Object> payload) {
						called.set(true);
						assertThat(info.getId()).isEqualTo("1");
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
				new WebSocketGraphQlInterceptor() {

					@Override
					public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo info, Map<String, Object> payload) {
						return Mono.error(new IllegalStateException());
					}
				});

		StepVerifier.create(session.getOutput()).verifyComplete();
		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4401, "Unauthorized"))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void unauthorizedWithoutConnectionInit() {
		TestWebSocketSession session = handle(Flux.just(toWebSocketMessage(BOOK_SUBSCRIPTION)));

		StepVerifier.create(session.getOutput()).expectComplete().verify(TIMEOUT);
		StepVerifier.create(session.closeStatus()).expectNext(new CloseStatus(4401, "Unauthorized")).verifyComplete();
	}

	@Test
	void tooManyConnectionInitRequests() {
		TestWebSocketSession session = handle(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"type\":\"connection_init\"}")));

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.expectComplete()
				.verify(TIMEOUT);

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4429, "Too many initialisation requests"))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void connectionInitTimeout() {
		GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(
				initHandler(), ServerCodecConfigurer.create(), Duration.ofMillis(50));

		TestWebSocketSession session = new TestWebSocketSession(Flux.empty());
		handler.handle(session).block(TIMEOUT);

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4408, "Connection initialisation timeout"))
				.expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void subscriptionExists() {
		Flux<WebSocketMessage> messageFlux = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_SUBSCRIPTION),
				toWebSocketMessage(BOOK_SUBSCRIPTION));

		TestWebSocketSession session = handle(messageFlux, new ConsumeOneAndNeverCompleteInterceptor());

		// Collect messages until session closed
		List<GraphQlWebSocketMessage> messages = new ArrayList<>();
		session.getOutput().subscribe((message) -> messages.add(decode(message)));

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4409, "Subscriber for " + SUBSCRIPTION_ID + " already exists"))
				.expectComplete()
				.verify(TIMEOUT);

		assertThat(messages.size()).isEqualTo(2);
		assertThat(messages.get(0).resolvedType()).isEqualTo(GraphQlWebSocketMessageType.CONNECTION_ACK);
		assertThat(messages.get(1).resolvedType()).isEqualTo(GraphQlWebSocketMessageType.NEXT);
	}

	@Test
	void clientCompletion() {
		Sinks.Many<WebSocketMessage> input = Sinks.many().unicast().onBackpressureBuffer();
		input.tryEmitNext(toWebSocketMessage("{\"type\":\"connection_init\"}"));
		input.tryEmitNext(toWebSocketMessage(BOOK_SUBSCRIPTION));

		TestWebSocketSession session = handle(input.asFlux(), new ConsumeOneAndNeverCompleteInterceptor());

		String completeMessage = "{\"id\":\"" + SUBSCRIPTION_ID + "\",\"type\":\"complete\"}";

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.NEXT))
				.then(() -> input.tryEmitNext(toWebSocketMessage(completeMessage)))
				.as("Second subscription with same id is possible only if the first was properly removed")
				.then(() -> input.tryEmitNext(toWebSocketMessage(BOOK_SUBSCRIPTION)))
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.NEXT))
				.then(() -> input.tryEmitNext(toWebSocketMessage(completeMessage)))
				.verifyTimeout(Duration.ofMillis(500));
	}

	@Test
	void subscriptionErrorPayloadIsArray() {
		String query = "{" +
				"\"id\":\"" + SUBSCRIPTION_ID + "\"," +
				"\"type\":\"subscribe\"," +
				"\"payload\":{\"query\": \"subscription { greetings }\"}" +
				"}";

		String schema = "type Subscription { greetings: String! } type Query { greeting: String! }";

		TestWebSocketSession session = new TestWebSocketSession(Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(query)));

		WebGraphQlHandler webHandler = GraphQlSetup.schemaContent(schema)
				.subscriptionFetcher("greetings", env -> Flux.just("a", null, "b"))
				.toWebGraphQlHandler();

		new GraphQlWebSocketHandler(webHandler, ServerCodecConfigurer.create(), TIMEOUT)
				.handle(session).block(TIMEOUT);

		StepVerifier.create(session.getOutput())
				.consumeNextWith((message) -> assertMessageType(message, GraphQlWebSocketMessageType.CONNECTION_ACK))
				.consumeNextWith((message) -> {
					GraphQlWebSocketMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlWebSocketMessageType.NEXT);
					assertThat(actual.<Map<String, Object>>getPayload())
							.containsEntry("data", Collections.singletonMap("greetings", "a"));
				})
				.consumeNextWith((message) -> {
					GraphQlWebSocketMessage actual = decode(message);
					assertThat(actual.getId()).isEqualTo(SUBSCRIPTION_ID);
					assertThat(actual.resolvedType()).isEqualTo(GraphQlWebSocketMessageType.ERROR);
					List<Map<String, Object>> errors = actual.getPayload();
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0)).containsEntry("message", "Subscription error");
					assertThat(errors.get(0)).containsEntry("extensions",
							Collections.singletonMap("classification", ErrorType.INTERNAL_ERROR.name()));
				})
				.expectComplete()
				.verify(TIMEOUT);
	}

	private TestWebSocketSession handle(Flux<WebSocketMessage> input, WebGraphQlInterceptor... interceptors) {
		GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(
				initHandler(interceptors),
				ServerCodecConfigurer.create(),
				Duration.ofSeconds(60));

		TestWebSocketSession session = new TestWebSocketSession(input);
		handler.handle(session).block(TIMEOUT);
		return session;
	}

	private static WebSocketMessage toWebSocketMessage(String data) {
		DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(data.getBytes(StandardCharsets.UTF_8));
		return new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);
	}

	@SuppressWarnings("ConstantConditions")
	private GraphQlWebSocketMessage decode(WebSocketMessage message) {
		return (GraphQlWebSocketMessage) decoder.decode(DataBufferUtils.retain(message.getPayload()),
				ResolvableType.forClass(GraphQlWebSocketMessage.class), null, Collections.emptyMap());
	}

	private void assertMessageType(WebSocketMessage webSocketMessage, GraphQlWebSocketMessageType messageType) {
		GraphQlWebSocketMessage message = decode(webSocketMessage);
		assertThat(message.resolvedType()).isEqualTo(messageType);
		if (messageType != GraphQlWebSocketMessageType.CONNECTION_ACK && messageType != GraphQlWebSocketMessageType.PONG) {
			assertThat(message.getId()).isEqualTo(SUBSCRIPTION_ID);
		}
	}

}

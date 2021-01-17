/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.webflux;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.GraphQLDataFetchers;
import org.springframework.graphql.WebInterceptor;
import org.springframework.graphql.WebOutput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.lang.Nullable;
import org.springframework.util.ResourceUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.adapter.AbstractWebSocketSession;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

/**
 * Unit tests for {@link GraphQLWebSocketHandler}.
 */
public class GraphQLWebSocketHandlerTests {

	private static final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();

	private static final String SUBSCRIPTION_ID = "123";

	private static final String BOOK_SEARCH_QUERY = "{" +
			"\"id\":\"" + SUBSCRIPTION_ID + "\"," +
			"\"type\":\"subscribe\"," +
			"\"payload\":{\"query\": \"" +
			"  subscription TestSubscription {" +
			"    bookSearch(minPages: 200) {" +
			"      id" +
			"      name" +
			"      pageCount" +
			"      author" +
			"  }}\"}" +
			"}";


	@Test
	void query() throws Exception {
		String bookQuery = "{" +
				"\"id\":\"" + SUBSCRIPTION_ID + "\"," +
				"\"type\":\"subscribe\"," +
				"\"payload\":{\"query\": \"" +
				"  query TestQuery {" +
				"    bookById(id: \\\"book-1\\\"){ " +
				"      id" +
				"      name" +
				"      pageCount" +
				"      author" +
				"  }}\"}" +
				"}";
		
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(bookQuery));

		TestWebSocketSession session = new TestWebSocketSession(input);
		initWebSocketHandler().handle(session).block();

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.consumeNextWith(message ->
						assertThat(decode(message))
								.hasSize(3)
								.containsEntry("id", SUBSCRIPTION_ID)
								.containsEntry("type", "next")
								.extractingByKey("payload", as(map(String.class, Object.class)))
								.extractingByKey("data", as(map(String.class, Object.class)))
								.extractingByKey("bookById", as(map(String.class, Object.class)))
								.containsEntry("name", "GraphQL for beginners"))
				.consumeNextWith(message -> assertMessageType(message, "complete"))
				.verifyComplete();
	}

	@Test
	void subscription() throws Exception {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_SEARCH_QUERY));

		TestWebSocketSession session = new TestWebSocketSession(input);
		initWebSocketHandler().handle(session).block();

		BiConsumer<WebSocketMessage, String> bookPayloadAssertion = (message, bookId) ->
				assertThat(decode(message))
						.hasSize(3)
						.containsEntry("id", SUBSCRIPTION_ID)
						.containsEntry("type", "next")
						.extractingByKey("payload", as(map(String.class, Object.class)))
						.extractingByKey("data", as(map(String.class, Object.class)))
						.extractingByKey("bookSearch", as(map(String.class, Object.class)))
						.containsEntry("id", bookId);

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.consumeNextWith(message -> bookPayloadAssertion.accept(message, "book-2"))
				.consumeNextWith(message -> bookPayloadAssertion.accept(message, "book-3"))
				.consumeNextWith(message -> bookPayloadAssertion.accept(message, "book-3"))
				.consumeNextWith(message -> assertMessageType(message, "complete"))
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutMessageType() throws Exception {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"id\":\"" + SUBSCRIPTION_ID + "\"}")); // No message type

		TestWebSocketSession session = new TestWebSocketSession(input);
		initWebSocketHandler().handle(session).block();

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.verifyComplete();
	}

	@Test
	void invalidMessageWithoutId() throws Exception {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"type\":\"subscribe\"}"));  // No message id

		TestWebSocketSession session = new TestWebSocketSession(input);
		initWebSocketHandler().handle(session).block();

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4400, "Invalid message"))
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutConnectionInit() throws Exception {
		TestWebSocketSession session = new TestWebSocketSession(Flux.just(toWebSocketMessage(BOOK_SEARCH_QUERY)));
		initWebSocketHandler().handle(session).block();

		StepVerifier.create(session.getOutput()).verifyComplete();
		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4401, "Unauthorized"))
				.verifyComplete();
	}

	@Test
	void tooManyConnectionInitRequests() throws Exception {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage("{\"type\":\"connection_init\"}"));

		TestWebSocketSession session = new TestWebSocketSession(input);
		initWebSocketHandler().handle(session).block();

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4429, "Too many initialisation requests"))
				.verifyComplete();
	}

	@Test
	void connectionInitTimeout() throws Exception {
		TestWebSocketSession session = new TestWebSocketSession(Flux.empty());
		initWebSocketHandler(Collections.emptyList(), Duration.ofMillis(50)).handle(session).block();

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4408, "Connection initialisation timeout"))
				.verifyComplete();
	}

	@Test
	void subscriptionExists() throws Exception {
		Flux<WebSocketMessage> input = Flux.just(
				toWebSocketMessage("{\"type\":\"connection_init\"}"),
				toWebSocketMessage(BOOK_SEARCH_QUERY),
				toWebSocketMessage(BOOK_SEARCH_QUERY));

		List<WebInterceptor> interceptors = Collections.singletonList(new TakeOneAndNeverCompleteInterceptor());

		TestWebSocketSession session = new TestWebSocketSession(input);
		initWebSocketHandler(interceptors, null).handle(session).block();

		// Collect messages until session closed
		List<Map<String, Object>> messages = new ArrayList<>();
		session.getOutput().subscribe(message -> messages.add(decode(message)));

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4409, "Subscriber for " + SUBSCRIPTION_ID + " already exists"))
				.verifyComplete();

		assertThat(messages.size()).isEqualTo(2);
		assertThat(messages.get(0).get("type")).isEqualTo("connection_ack");
		assertThat(messages.get(1).get("type")).isEqualTo("next");
	}

	@Test
	void clientCompletion() throws Exception {
		Sinks.Many<WebSocketMessage> input = Sinks.many().unicast().onBackpressureBuffer();
		input.tryEmitNext(toWebSocketMessage("{\"type\":\"connection_init\"}"));
		input.tryEmitNext(toWebSocketMessage(BOOK_SEARCH_QUERY));

		List<WebInterceptor> interceptors = Collections.singletonList(new TakeOneAndNeverCompleteInterceptor());
		TestWebSocketSession session = new TestWebSocketSession(input.asFlux());
		initWebSocketHandler(interceptors, null).handle(session).block();

		String completeMessage = "{\"id\":\"" + SUBSCRIPTION_ID + "\",\"type\":\"complete\"}";

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.consumeNextWith(message -> assertMessageType(message, "next"))
				.then(() -> input.tryEmitNext(toWebSocketMessage(completeMessage)))
				.as("Second subscription with same id is possible only if the first was properly removed")
				.then(() -> input.tryEmitNext(toWebSocketMessage(BOOK_SEARCH_QUERY)))
				.consumeNextWith(message -> assertMessageType(message, "next"))
				.then(() -> input.tryEmitNext(toWebSocketMessage(completeMessage)))
				.verifyTimeout(Duration.ofMillis(500));
	}

	private GraphQLWebSocketHandler initWebSocketHandler() throws Exception {
		return initWebSocketHandler(Collections.emptyList(), Duration.ofSeconds(69));
	}

	private GraphQLWebSocketHandler initWebSocketHandler(
			@Nullable List<WebInterceptor> interceptors, @Nullable Duration initTimeoutDuration) throws Exception {

		GraphQL graphQL = initGraphQL();
		return new GraphQLWebSocketHandler(graphQL,
				(interceptors != null ? interceptors : Collections.emptyList()),
				ServerCodecConfigurer.create(),
				(initTimeoutDuration != null ? initTimeoutDuration : Duration.ofSeconds(60)));
	}

	private static GraphQL initGraphQL() throws Exception {
		File schemaFile = ResourceUtils.getFile("classpath:books/schema.graphqls");
		TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(schemaFile);

		RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
		builder.type(newTypeWiring("Query").dataFetcher("bookById", GraphQLDataFetchers.getBookByIdDataFetcher()));
		builder.type(newTypeWiring("Subscription").dataFetcher("bookSearch", GraphQLDataFetchers.getBooksOnSale()));
		RuntimeWiring runtimeWiring = builder.build();

		GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
		return GraphQL.newGraphQL(schema).build();
	}

	private static WebSocketMessage toWebSocketMessage(String data) {
		DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(data.getBytes(StandardCharsets.UTF_8));
		return new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);
	}

	@SuppressWarnings({"unchecked", "ConstantConditions"})
	private Map<String, Object> decode(WebSocketMessage message) {
		return (Map<String, Object>) decoder.decode(
				DataBufferUtils.retain(message.getPayload()),
				GraphQLWebSocketHandler.MAP_RESOLVABLE_TYPE, null, Collections.emptyMap());
	}

	private void assertMessageType(WebSocketMessage message, String messageType) {
		Map<String, Object> map = decode(message);
		assertThat(map).containsEntry("type", messageType);
		if (!messageType.equals("connection_ack")) {
			assertThat(map).containsEntry("id", SUBSCRIPTION_ID);
		}
	}


	private static class TestWebSocketSession extends AbstractWebSocketSession<Object> {

		private final Flux<WebSocketMessage> input;

		private Flux<WebSocketMessage> output = Flux.empty();

		private final Sinks.One<CloseStatus> closeStatusSink = Sinks.one();


		public TestWebSocketSession(Flux<WebSocketMessage> input) {
			this("1", URI.create("https://example.org/graphql"), input);
		}

		public TestWebSocketSession(String id, URI uri, Flux<WebSocketMessage> input) {
			super(new Object(), id,
					new HandshakeInfo(uri, new HttpHeaders(), Mono.empty(), null),
					DefaultDataBufferFactory.sharedInstance);
			this.input = input;
		}


		@Override
		public Flux<WebSocketMessage> receive() {
			return this.input;
		}

		@Override
		public Mono<Void> send(Publisher<WebSocketMessage> messages) {
			this.output = Flux.from(messages);
			return Mono.empty();
		}

		public Flux<WebSocketMessage> getOutput() {
			return this.output;
		}

		@Override
		public boolean isOpen() {
			throw new java.lang.UnsupportedOperationException();
		}

		@Override
		public Mono<Void> close(CloseStatus status) {
			this.closeStatusSink.tryEmitValue(status);
			return Mono.empty();
		}

		@Override
		public Mono<CloseStatus> closeStatus() {
			return this.closeStatusSink.asMono();
		}
	}


	private static class TakeOneAndNeverCompleteInterceptor implements WebInterceptor {

		@Override
		public Mono<WebOutput> postHandle(WebOutput output) {
			return Mono.just(output.transform(builder -> {
				Publisher<?> publisher = output.getData();
				assertThat(publisher).isNotNull();
				builder.data(Flux.from(publisher).take(1).concatWith(Flux.never()));
			}));
		}
	}
}

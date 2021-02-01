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
package org.springframework.graphql.webmvc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.graphql.ConsumeOneAndNeverCompleteInterceptor;
import org.springframework.graphql.GraphQLDataFetchers;
import org.springframework.graphql.WebInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.ResourceUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

/**
 * Unit tests for {@link GraphQLWebSocketHandler}.
 */
public class GraphQLWebSocketHandlerTests {

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

	private static final HttpMessageConverter<?> converter = new MappingJackson2HttpMessageConverter();

	
	private final TestWebSocketSession session = new TestWebSocketSession();

	private final GraphQLWebSocketHandler handler =
			initWebSocketHandler(Collections.emptyList(), Duration.ofSeconds(60));


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

		this.handler.afterConnectionEstablished(session);
		this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));
		this.handler.handleTextMessage(session, new TextMessage(bookQuery));

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
				.then(session::close)  // Complete output Flux
				.verifyComplete();
	}

	@Test
	void subscription() throws Exception {
		this.handler.afterConnectionEstablished(session);
		this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));
		this.handler.handleTextMessage(session, new TextMessage(BOOK_SEARCH_QUERY));

		BiConsumer<WebSocketMessage<?>, String> bookPayloadAssertion = (message, bookId) ->
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
				.then(session::close)  // Complete output Flux
				.verifyComplete();
	}

	@Test
	void unauthorizedWithoutMessageType() throws Exception {
		this.handler.afterConnectionEstablished(session);
		this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));
		this.handler.handleTextMessage(session, new TextMessage("{\"id\":\"" + SUBSCRIPTION_ID + "\"}")); // No message type

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		assertThat(session.getCloseStatus()).isEqualTo(new CloseStatus(4400, "Invalid message"));
	}

	@Test
	void invalidMessageWithoutId() throws Exception {
		this.handler.afterConnectionEstablished(session);
		this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));
		this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"subscribe\"}"));  // No message id

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		assertThat(session.getCloseStatus()).isEqualTo(new CloseStatus(4400, "Invalid message"));
	}

	@Test
	void unauthorizedWithoutConnectionInit() throws Exception {
		this.handler.afterConnectionEstablished(session);
		this.handler.handleTextMessage(session, new TextMessage(BOOK_SEARCH_QUERY));

		StepVerifier.create(session.getOutput()).verifyComplete();
		assertThat(session.getCloseStatus()).isEqualTo(new CloseStatus(4401, "Unauthorized"));
	}

	@Test
	void tooManyConnectionInitRequests() throws Exception {
		this.handler.afterConnectionEstablished(session);
		this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));
		this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.verifyComplete();

		assertThat(session.getCloseStatus())
				.isEqualTo(new CloseStatus(4429, "Too many initialisation requests"));
	}

	@Test
	void connectionInitTimeout() {
		GraphQLWebSocketHandler handler = initWebSocketHandler(Collections.emptyList(), Duration.ofMillis(50));
		handler.afterConnectionEstablished(session);

		StepVerifier.create(session.closeStatus())
				.expectNext(new CloseStatus(4408, "Connection initialisation timeout"))
				.verifyComplete();
	}

	@Test
	void subscriptionExists() throws Exception {
		GraphQLWebSocketHandler handler = initWebSocketHandler(
				Collections.singletonList(new ConsumeOneAndNeverCompleteInterceptor()), null);

		handler.afterConnectionEstablished(session);
		handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));
		handler.handleTextMessage(session, new TextMessage(BOOK_SEARCH_QUERY));
		handler.handleTextMessage(session, new TextMessage(BOOK_SEARCH_QUERY));

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
		GraphQLWebSocketHandler handler = initWebSocketHandler(
				Collections.singletonList(new ConsumeOneAndNeverCompleteInterceptor()), null);

		handler.afterConnectionEstablished(session);
		handler.handleTextMessage(session, new TextMessage("{\"type\":\"connection_init\"}"));
		handler.handleTextMessage(session, new TextMessage(BOOK_SEARCH_QUERY));

		String completeMessage = "{\"id\":\"" + SUBSCRIPTION_ID + "\",\"type\":\"complete\"}";
		Consumer<String> messageSender = body -> {
			try {
				handler.handleTextMessage(session, new TextMessage(body));
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		};

		StepVerifier.create(session.getOutput())
				.consumeNextWith(message -> assertMessageType(message, "connection_ack"))
				.consumeNextWith(message -> assertMessageType(message, "next"))
				.then(() -> messageSender.accept(completeMessage))
				.as("Second subscription with same id is possible only if the first was properly removed")
				.then(() -> messageSender.accept(BOOK_SEARCH_QUERY))
				.consumeNextWith(message -> assertMessageType(message, "next"))
				.then(() -> messageSender.accept(completeMessage))
				.verifyTimeout(Duration.ofMillis(500));
	}


	private GraphQLWebSocketHandler initWebSocketHandler(
			@Nullable List<WebInterceptor> interceptors, @Nullable Duration initTimeoutDuration) {

		try {
			return new GraphQLWebSocketHandler(initGraphQL(),
					(interceptors != null ? interceptors : Collections.emptyList()), converter,
					(initTimeoutDuration != null ? initTimeoutDuration : Duration.ofSeconds(60)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
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

	private void assertMessageType(WebSocketMessage<?> message, String messageType) {
		Map<String, Object> map = decode(message, Map.class);
		assertThat(map).containsEntry("type", messageType);
		if (!messageType.equals("connection_ack")) {
			assertThat(map).containsEntry("id", SUBSCRIPTION_ID);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> decode(WebSocketMessage<?> message) {
		return decode(message, Map.class);
	}

	@SuppressWarnings("unchecked")
	private <T> T decode(WebSocketMessage<?> message, Class<T> targetClass) {
		try {
			HttpInputMessageAdapter inputMessage = new HttpInputMessageAdapter((TextMessage) message);
			return ((HttpMessageConverter<T>) converter).read(targetClass, inputMessage);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
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

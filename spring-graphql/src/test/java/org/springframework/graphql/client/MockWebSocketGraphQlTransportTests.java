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

package org.springframework.graphql.client;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.web.webflux.GraphQlWebSocketMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebSocketGraphQlTransport}.
 * @author Rossen Stoyanchev
 */
public class MockWebSocketGraphQlTransportTests {

	private final static Duration TIMEOUT = Duration.ofSeconds(5);

	private static final WebSocketCodecDelegate CODEC_DELEGATE = new WebSocketCodecDelegate();


	private final MockWebSocketServer mockServer = new MockWebSocketServer();

	private final TestWebSocketClient testClient = new TestWebSocketClient(this.mockServer);
	
	private final WebSocketGraphQlTransport transport = 
			WebSocketGraphQlTransport.builder(URI.create("/"), this.testClient).build();

	private final ExecutionResult result1 = MapExecutionResult.forData(Collections.singletonMap("key1", "value1"));

	private final ExecutionResult result2 = MapExecutionResult.forData(Collections.singletonMap("key2", "value2"));


	@Test
	void request() {
		RequestInput input = this.mockServer.expectOperation("Query1").andRespond(this.result1);

		StepVerifier.create(this.transport.execute(input))
				.expectNext(this.result1).expectComplete()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", input));
	}

	@Test
	void requestStream() {
		RequestInput input = this.mockServer.expectOperation("Sub1").andStream(Flux.just(this.result1, result2));

		StepVerifier.create(this.transport.executeSubscription(input))
				.expectNext(this.result1, result2).expectComplete()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", input));
	}

	@Test
	void requestError() {
		RequestInput input = this.mockServer.expectOperation("Query1")
				.andRespondWithError(GraphqlErrorBuilder.newError().message("boo").build());

		StepVerifier.create(this.transport.execute(input))
				.consumeNextWith(result -> {
					assertThat(result.isDataPresent()).isFalse();
					assertThat(result.getErrors()).extracting(GraphQLError::getMessage).containsExactly("boo");
				})
				.expectComplete()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", input));
	}

	@Test
	void requestStreamError() {
		RequestInput input = this.mockServer.expectOperation("Sub1")
				.andStreamWithError(Flux.just(this.result1), GraphqlErrorBuilder.newError().message("boo").build());

		StepVerifier.create(this.transport.executeSubscription(input))
				.expectNext(this.result1)
				.expectErrorSatisfies(actualEx -> {
					List<GraphQLError> errorList = ((SubscriptionErrorException) actualEx).getErrors();
					assertThat(errorList).extracting(GraphQLError::getMessage).containsExactly("boo");
				})
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", input));
	}

	@Test
	void requestCancelled() {
		RequestInput input = this.mockServer.expectOperation("Query1").andRespond(Mono.never());

		StepVerifier.create(this.transport.execute(input))
				.thenAwait(Duration.ofMillis(200))
				.thenCancel()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", input));
	}

	@Test
	void requestStreamCancelled() {
		RequestInput input = this.mockServer.expectOperation("s1")
				.andStream(Flux.just(this.result1).concatWith(Flux.never()));

		StepVerifier.create(this.transport.executeSubscription(input))
				.expectNext(this.result1)
				.thenAwait(Duration.ofMillis(200))
				.thenCancel()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", input),
				GraphQlWebSocketMessage.complete("1"));
	}

	@Test
	void start() {
		MockWebSocketServer handler = new MockWebSocketServer();
		handler.connectionInitHandler(payload -> Mono.just(Collections.singletonMap("key", payload.get("key") + "Ack")));

		TestWebSocketClient client = new TestWebSocketClient(handler);
		Map<String, String> initPayload = Collections.singletonMap("key", "valueInit");
		AtomicReference<Map<String, Object>> connectionAckRef = new AtomicReference<>();

		WebSocketGraphQlTransport transport = WebSocketGraphQlTransport.builder(URI.create("/"), client)
				.connectionInitPayload(initPayload)
				.connectionAckHandler(connectionAckRef::set)
				.build();

		transport.start().block(TIMEOUT);

		assertThat(client.getConnection(0).isOpen()).isTrue();
		assertThat(connectionAckRef.get()).isEqualTo(Collections.singletonMap("key", "valueInitAck"));
		assertActualClientMessages(client.getConnection(0), GraphQlWebSocketMessage.connectionInit(initPayload));
	}

	@Test
	void stop() {

		// Start
		this.transport.start().block(TIMEOUT);
		assertThat(this.testClient.getConnectionCount()).isEqualTo(1);
		assertThat(this.testClient.getConnection(0).isOpen()).isTrue();

		// Stop
		this.transport.stop().block(TIMEOUT);
		assertThat(this.testClient.getConnection(0).isOpen()).isFalse();
		assertThat(this.testClient.getConnection(0).closeStatus().block(TIMEOUT)).isEqualTo(CloseStatus.NORMAL);

		// New requests are rejected
		RequestInput input = this.mockServer.expectOperation("Query1").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(input))
				.expectErrorMessage("WebSocketGraphQlTransport has been stopped")
				.verify(TIMEOUT);

		// Start
		this.transport.start().block(TIMEOUT);
		assertThat(this.testClient.getConnectionCount()).isEqualTo(2);
		assertThat(this.testClient.getConnection(1).isOpen()).isTrue();

		// Requests allowed again
		input = this.mockServer.expectOperation("Query1").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(input))
				.expectNext(this.result1).expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void sessionIsCachedUntilClosed() {

		RequestInput input1 = this.mockServer.expectOperation("Query1").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(input1)).expectNext(this.result1).expectComplete().verify(TIMEOUT);

		assertThat(this.testClient.getConnectionCount()).isEqualTo(1);
		TestWebSocketConnection originalConnection = this.testClient.getConnection(0);

		RequestInput input2 = this.mockServer.expectOperation("Query2").andRespond(this.result2);
		StepVerifier.create(this.transport.execute(input2)).expectNext(this.result2).expectComplete().verify(TIMEOUT);

		assertThat(this.testClient.getConnectionCount()).isEqualTo(1);
		assertThat(this.testClient.getConnection(0)).isSameAs(originalConnection);

		// Close the connection
		originalConnection.closeServerSession(CloseStatus.NORMAL).block(TIMEOUT);

		input1 = this.mockServer.expectOperation("Query1").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(input1)).expectNext(this.result1).expectComplete().verify(TIMEOUT);

		assertThat(this.testClient.getConnectionCount()).isEqualTo(2);
		assertThat(this.testClient.getConnection(1)).isNotSameAs(originalConnection);
	}

	@Test
	void errorOnConnect() {

		// Connection errors should be routed, no hanging on start

		IOException ex = new IOException("Connect failure");

		WebSocketClient client = mock(WebSocketClient.class);
		when(client.execute(any(URI.class), any(HttpHeaders.class), any(WebSocketHandler.class))).thenReturn(Mono.error(ex));

		StepVerifier.create(createTransport(client).start())
				.expectErrorMessage(ex.getMessage())
				.verify(TIMEOUT);
	}

	@Test
	void errorBeforeConnectionAck() {

		// Errors before GraphQL session initialized should be routed, no hanging on start

		MockWebSocketServer handler = new MockWebSocketServer();
		handler.connectionInitHandler(initPayload -> Mono.error(new IllegalStateException("boo")));

		TestWebSocketClient client = new TestWebSocketClient(handler);

		StepVerifier.create(createTransport(client).start())
				.expectErrorMessage("boo")
				.verify(TIMEOUT);
	}

	@Test
	void errorDuringResponseHandling() {

		// Response handling errors that close the connection should terminate outstanding requests

		TestWebSocketClient client = new TestWebSocketClient(new UnexpectedResponseHandler());
		WebSocketGraphQlTransport transport = createTransport(client);

		String expectedMessage = "GraphQlSession over client-session-1 disconnected " +
				"with CloseStatus[code=1002, reason=null]";

		StepVerifier.create(transport.execute(new RequestInput("Query1", null, null, null, "")))
				.expectErrorMessage(expectedMessage)
				.verify(TIMEOUT);
	}

	private WebSocketGraphQlTransport createTransport(WebSocketClient client) {
		return WebSocketGraphQlTransport.builder(URI.create("/"), client).build();
	}

	private void assertActualClientMessages(GraphQlWebSocketMessage... expectedMessages) {
		assertActualClientMessages(this.testClient.getConnection(0), expectedMessages);
	}

	private void assertActualClientMessages(
			TestWebSocketConnection connection, GraphQlWebSocketMessage... expectedMessages) {

		List<GraphQlWebSocketMessage> actualMessages = connection.getClientMessages().stream()
				.map(CODEC_DELEGATE::decode)
				.collect(Collectors.toList());

		assertThat(actualMessages).containsExactly(expectedMessages);
	}


	/**
	 * Server handler that returns an unexpected (client) message.
	 */
	private static class UnexpectedResponseHandler implements WebSocketHandler {

		private final WebSocketCodecDelegate codecDelegate = new WebSocketCodecDelegate();


		@Override
		public Mono<Void> handle(WebSocketSession session) {
			return session.send(session.receive().flatMap(webSocketMessage -> {

				GraphQlWebSocketMessage requestMessage = this.codecDelegate.decode(webSocketMessage);
				String id = requestMessage.getId();

				GraphQlWebSocketMessage responseMessage = (requestMessage.getType().equals("connection_init") ?
						GraphQlWebSocketMessage.connectionAck(null) :
						GraphQlWebSocketMessage.subscribe(id, new RequestInput("..", null, null, null, "")));

				return Flux.just(this.codecDelegate.encode(session, responseMessage));
			}));
		}

	}

}

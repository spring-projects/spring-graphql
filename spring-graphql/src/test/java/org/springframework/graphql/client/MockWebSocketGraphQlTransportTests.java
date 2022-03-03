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

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.support.MapExecutionResult;
import org.springframework.graphql.web.webflux.GraphQlWebSocketMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link WebSocketGraphQlTransport} using {@link TestWebSocketClient}
 * and {@link MockGraphQlWebSocketServer}.
 *
 * @author Rossen Stoyanchev
 */
public class MockWebSocketGraphQlTransportTests {

	private final static Duration TIMEOUT = Duration.ofSeconds(5);

	private static final WebSocketCodecDelegate CODEC_DELEGATE = new WebSocketCodecDelegate();


	private final MockGraphQlWebSocketServer mockServer = new MockGraphQlWebSocketServer();

	private final TestWebSocketClient webSocketClient = new TestWebSocketClient(this.mockServer);
	
	private final WebSocketGraphQlTransport transport = createTransport(this.webSocketClient);

	private final ExecutionResult result1 = MapExecutionResult.forDataOnly(Collections.singletonMap("key1", "value1"));

	private final ExecutionResult result2 = MapExecutionResult.forDataOnly(Collections.singletonMap("key2", "value2"));


	@Test
	void request() {
		GraphQlRequest request = this.mockServer.expectOperation("{Query1}").andRespond(this.result1);

		StepVerifier.create(this.transport.execute(request))
				.expectNext(this.result1).expectComplete()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", request));
	}

	@Test
	void requestStream() {
		GraphQlRequest request = this.mockServer.expectOperation("{Sub1}").andStream(Flux.just(this.result1, result2));

		StepVerifier.create(this.transport.executeSubscription(request))
				.expectNext(this.result1, result2).expectComplete()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", request));
	}

	@Test
	void requestError() {
		GraphQlRequest request = this.mockServer.expectOperation("{Query1}")
				.andRespondWithError(GraphqlErrorBuilder.newError().message("boo").build());

		StepVerifier.create(this.transport.execute(request))
				.consumeNextWith(result -> {
					assertThat(result.isDataPresent()).isFalse();
					assertThat(result.getErrors()).extracting(GraphQLError::getMessage).containsExactly("boo");
				})
				.expectComplete()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", request));
	}

	@Test
	void requestStreamError() {
		GraphQlRequest request = this.mockServer.expectOperation("{Sub1}")
				.andStreamWithError(Flux.just(this.result1), GraphqlErrorBuilder.newError().message("boo").build());

		StepVerifier.create(this.transport.executeSubscription(request))
				.expectNext(this.result1)
				.expectErrorSatisfies(actualEx -> {
					List<GraphQLError> errorList = ((SubscriptionErrorException) actualEx).getErrors();
					assertThat(errorList).extracting(GraphQLError::getMessage).containsExactly("boo");
				})
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", request));
	}

	@Test
	void requestCancelled() {
		GraphQlRequest request = this.mockServer.expectOperation("{Query1}").andRespond(Mono.never());

		StepVerifier.create(this.transport.execute(request))
				.thenAwait(Duration.ofMillis(200))
				.thenCancel()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", request));
	}

	@Test
	void requestStreamCancelled() {
		GraphQlRequest request = this.mockServer.expectOperation("{Sub1}")
				.andStream(Flux.just(this.result1).concatWith(Flux.never()));

		StepVerifier.create(this.transport.executeSubscription(request))
				.expectNext(this.result1)
				.thenAwait(Duration.ofMillis(200))
				.thenCancel()
				.verify(TIMEOUT);

		assertActualClientMessages(
				GraphQlWebSocketMessage.connectionInit(null),
				GraphQlWebSocketMessage.subscribe("1", request),
				GraphQlWebSocketMessage.complete("1"));
	}

	@Test
	void start() {
		MockGraphQlWebSocketServer handler = new MockGraphQlWebSocketServer();
		handler.connectionInitHandler(payload -> Mono.just(Collections.singletonMap("key", payload.get("key") + "Ack")));

		TestWebSocketClient client = new TestWebSocketClient(handler);
		Map<String, String> initPayload = Collections.singletonMap("key", "valueInit");
		AtomicReference<Map<String, Object>> connectionAckRef = new AtomicReference<>();

		WebSocketGraphQlTransport transport = new WebSocketGraphQlTransport(
				URI.create("/"), HttpHeaders.EMPTY, client, ClientCodecConfigurer.create(),
				initPayload, connectionAckRef::set);

		transport.start().block(TIMEOUT);

		assertThat(client.getConnection(0).isOpen()).isTrue();
		assertThat(connectionAckRef.get()).isEqualTo(Collections.singletonMap("key", "valueInitAck"));
		assertActualClientMessages(client.getConnection(0), GraphQlWebSocketMessage.connectionInit(initPayload));
	}

	@Test
	void stop() {

		// Start
		this.transport.start().block(TIMEOUT);
		assertThat(this.webSocketClient.getConnectionCount()).isEqualTo(1);
		assertThat(this.webSocketClient.getConnection(0).isOpen()).isTrue();

		// Stop
		this.transport.stop().block(TIMEOUT);
		assertThat(this.webSocketClient.getConnection(0).isOpen()).isFalse();
		assertThat(this.webSocketClient.getConnection(0).closeStatus().block(TIMEOUT)).isEqualTo(CloseStatus.NORMAL);

		// New requests are rejected
		GraphQlRequest request = this.mockServer.expectOperation("{Query1}").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(request))
				.expectErrorMessage("WebSocketGraphQlTransport has been stopped")
				.verify(TIMEOUT);

		// Start
		this.transport.start().block(TIMEOUT);
		assertThat(this.webSocketClient.getConnectionCount()).isEqualTo(2);
		assertThat(this.webSocketClient.getConnection(1).isOpen()).isTrue();

		// Requests allowed again
		request = this.mockServer.expectOperation("{Query1}").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(request))
				.expectNext(this.result1).expectComplete()
				.verify(TIMEOUT);
	}

	@Test
	void sessionIsCachedUntilClosed() {

		GraphQlRequest request1 = this.mockServer.expectOperation("{Query1}").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(request1)).expectNext(this.result1).expectComplete().verify(TIMEOUT);

		assertThat(this.webSocketClient.getConnectionCount()).isEqualTo(1);
		TestWebSocketConnection originalConnection = this.webSocketClient.getConnection(0);

		GraphQlRequest request2 = this.mockServer.expectOperation("{Query2}").andRespond(this.result2);
		StepVerifier.create(this.transport.execute(request2)).expectNext(this.result2).expectComplete().verify(TIMEOUT);

		assertThat(this.webSocketClient.getConnectionCount()).isEqualTo(1);
		assertThat(this.webSocketClient.getConnection(0)).isSameAs(originalConnection);

		// Close the connection
		originalConnection.closeServerSession(CloseStatus.NORMAL).block(TIMEOUT);

		request1 = this.mockServer.expectOperation("{Query1}").andRespond(this.result1);
		StepVerifier.create(this.transport.execute(request1)).expectNext(this.result1).expectComplete().verify(TIMEOUT);

		assertThat(this.webSocketClient.getConnectionCount()).isEqualTo(2);
		assertThat(this.webSocketClient.getConnection(1)).isNotSameAs(originalConnection);
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

		MockGraphQlWebSocketServer handler = new MockGraphQlWebSocketServer();
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

		String expectedMessage = "disconnected with CloseStatus[code=1002, reason=null]";

		StepVerifier.create(transport.execute(new GraphQlRequest("{Query1}")))
				.expectErrorSatisfies(ex -> assertThat(ex).hasMessageEndingWith(expectedMessage))
				.verify(TIMEOUT);
	}

	private static WebSocketGraphQlTransport createTransport(WebSocketClient client) {
		return new WebSocketGraphQlTransport(
				URI.create("/"), HttpHeaders.EMPTY, client, ClientCodecConfigurer.create(), null, p -> {});
	}

	private void assertActualClientMessages(GraphQlWebSocketMessage... expectedMessages) {
		assertActualClientMessages(this.webSocketClient.getConnection(0), expectedMessages);
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
						GraphQlWebSocketMessage.subscribe(id, new GraphQlRequest("")));

				return Flux.just(this.codecDelegate.encode(session, responseMessage));
			}));
		}

	}

}

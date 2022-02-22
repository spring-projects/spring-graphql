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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.web.webflux.GraphQlWebSocketMessage;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * GraphQL over WebSocket handler to use as a server-side
 * {@link WebSocketHandler} that is configured with expected requests and
 * the responses to send.
 *
 * @author Rossen Stoyanchev
 */
public class MockWebSocketServer implements WebSocketHandler {

	@Nullable
	private Function<Map<String, Object>, Mono<Object>> connectionInitHandler;

	private final Map<Map<String, Object>, Exchange> expectedExchanges = new LinkedHashMap<>();

	private final WebSocketCodecDelegate codecDelegate = new WebSocketCodecDelegate();


	/**
	 * Configure a handler for the "connection_init" message.
	 * @param handler accepts "connection_init" and returns the "connection_ack" payload
	 */
	public void connectionInitHandler(Function<Map<String, Object>, Mono<Object>> handler) {
		this.connectionInitHandler = handler;
	}

	/**
	 * Add the GraphQL operation for an expected request and then specify the
	 * response to send back.
	 */
	public ResponseSpec expectOperation(String operation) {
		Exchange exchange = new Exchange(operation);
		this.expectedExchanges.put(exchange.getInput().toMap(), exchange);
		return exchange;
	}


	@Override
	public Mono<Void> handle(WebSocketSession session) {
		return session.send(session.receive()
				.map(codecDelegate::decode)
				.flatMap(this::handleMessage)
				.map(message -> codecDelegate.encode(session, message)));
	}

	@SuppressWarnings("SuspiciousMethodCalls")
	private Publisher<GraphQlWebSocketMessage> handleMessage(GraphQlWebSocketMessage message) {
		if ("connection_init".equals(message.getType())) {
			if (this.connectionInitHandler == null) {
				return Flux.just(GraphQlWebSocketMessage.connectionAck(null));
			}
			else {
				Map<String, Object> payload = message.getPayload();
				return this.connectionInitHandler.apply(payload).map(GraphQlWebSocketMessage::connectionAck);
			}
		}
		if ("subscribe".equals(message.getType())) {
			String id = message.getId();
			Exchange request = expectedExchanges.get(message.getPayload());
			if (id == null || request == null) {
				return Flux.error(new IllegalStateException("Unexpected request: " + message));
			}
			return request.getResponseFlux()
					.map(result -> GraphQlWebSocketMessage.next(id, result))
					.concatWithValues(
							request.getError() != null ?
									GraphQlWebSocketMessage.error(id, request.getError()) :
									GraphQlWebSocketMessage.complete(id));
		}
		if ("complete".equals(message.getType())) {
			return Flux.empty();
		}
		return Flux.error(new IllegalStateException("Unexpected message: " + message));
	}


	public interface ResponseSpec {

		/**
		 * Respond with the given a single result.
		 */
		RequestInput andRespond(ExecutionResult result);

		/**
		 * Respond with the given a single result {@code Mono}.
		 */
		RequestInput andRespond(Mono<ExecutionResult> resultMono);

		/**
		 * Respond with a GraphQL over WebSocket "error" message.
		 */
		RequestInput andRespondWithError(GraphQLError error);

		/**
		 * Respond with the given stream of responses.
		 */
		RequestInput andStream(Flux<ExecutionResult> resultFlux);

		/**
		 * Respond with the given stream of responses and terminate with an error.
		 */
		RequestInput andStreamWithError(Flux<ExecutionResult> resultFlux, GraphQLError error);

	}


	private static class Exchange implements ResponseSpec {

		private final RequestInput requestInput;

		private Flux<ExecutionResult> responseFlux = Flux.empty();

		@Nullable
		private GraphQLError error;


		private Exchange(String operation) {
			this.requestInput = new RequestInput(operation, null, null, null, "");
		}

		@Override
		public RequestInput andRespond(ExecutionResult result) {
			return addResponse(Flux.just(result), null);
		}

		@Override
		public RequestInput andRespond(Mono<ExecutionResult> resultMono) {
			return addResponse(Flux.from(resultMono), null);
		}

		@Override
		public RequestInput andRespondWithError(GraphQLError error) {
			return addResponse(Flux.empty(), error);
		}

		@Override
		public RequestInput andStream(Flux<ExecutionResult> resultFlux) {
			return addResponse(resultFlux, null);
		}

		@Override
		public RequestInput andStreamWithError(Flux<ExecutionResult> resultFlux, GraphQLError error) {
			return addResponse(resultFlux, error);
		}

		private RequestInput addResponse(Flux<ExecutionResult> resultFlux, @Nullable GraphQLError error) {
			this.responseFlux = resultFlux;
			this.error = error;
			return this.requestInput;
		}

		public RequestInput getInput() {
			return this.requestInput;
		}

		public Flux<ExecutionResult> getResponseFlux() {
			return this.responseFlux;
		}

		@Nullable
		public GraphQLError getError() {
			return this.error;
		}

	}

}

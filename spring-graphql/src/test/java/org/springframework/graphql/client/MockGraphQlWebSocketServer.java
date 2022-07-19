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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import graphql.GraphQLError;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.server.support.GraphQlWebSocketMessage;
import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * GraphQL over WebSocket {@link WebSocketHandler} to use as a server-side
 * {@link WebSocketHandler} that is configured with expected requests and
 * the responses to send.
 *
 * @author Rossen Stoyanchev
 */
public final class MockGraphQlWebSocketServer implements WebSocketHandler {

	private final static Log logger = LogFactory.getLog(MockGraphQlWebSocketServer.class);


	@Nullable
	private Function<Map<String, Object>, Mono<Object>> connectionInitHandler;

	private final Map<Map<String, Object>, Exchange> expectedExchanges = new LinkedHashMap<>();

	private final CodecDelegate codecDelegate = new CodecDelegate(ClientCodecConfigurer.create());


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
		this.expectedExchanges.put(exchange.getRequest().toMap(), exchange);
		return exchange;
	}


	@Override
	public Mono<Void> handle(WebSocketSession session) {
		return session.send(session.receive()
				.map(codecDelegate::decode)
				.flatMap(this::handleMessage)
				.map(message -> codecDelegate.encode(session, message)))
				.doOnError(ex -> logger.error("Session handling error: " + ex.getMessage(), ex));
	}

	@SuppressWarnings("SuspiciousMethodCalls")
	private Publisher<GraphQlWebSocketMessage> handleMessage(GraphQlWebSocketMessage message) {
		switch (message.resolvedType()) {
			case CONNECTION_INIT:
				if (this.connectionInitHandler == null) {
					return Flux.just(GraphQlWebSocketMessage.connectionAck(null));
				}
				else {
					Map<String, Object> payload = message.getPayload();
					return this.connectionInitHandler.apply(payload).map(GraphQlWebSocketMessage::connectionAck);
				}
			case SUBSCRIBE:
				String id = message.getId();
				Exchange request = expectedExchanges.get(message.getPayload());
				if (id == null || request == null) {
					return Flux.error(new IllegalStateException("Unexpected request: " + message));
				}
				return request.getResponseFlux()
						.map(response -> GraphQlWebSocketMessage.next(id, response.toMap()))
						.concatWithValues(
								request.getError() != null ?
										GraphQlWebSocketMessage.error(id, Collections.singletonList(request.getError())) :
										GraphQlWebSocketMessage.complete(id));
			case COMPLETE:
				return Flux.empty();
			default:
				return Flux.error(new IllegalStateException("Unexpected message: " + message));
		}
	}


	public interface ResponseSpec {

		/**
		 * Respond with the given a single result.
		 */
		GraphQlRequest andRespond(GraphQlResponse response);

		/**
		 * Respond with the given a single result {@code Mono}.
		 */
		GraphQlRequest andRespond(Mono<GraphQlResponse> responseMono);

		/**
		 * Respond with a GraphQL over WebSocket "error" message.
		 */
		GraphQlRequest andRespondWithError(GraphQLError error);

		/**
		 * Respond with the given stream of responses.
		 */
		GraphQlRequest andStream(Flux<GraphQlResponse> responseFlux);

		/**
		 * Respond with the given stream of responses and terminate with an error.
		 */
		GraphQlRequest andStreamWithError(Flux<GraphQlResponse> responseFlux, GraphQLError error);

	}


	private static class Exchange implements ResponseSpec {

		private final GraphQlRequest request;

		private Flux<GraphQlResponse> responseFlux = Flux.empty();

		@Nullable
		private GraphQLError error;


		private Exchange(String operation) {
			this.request = new DefaultGraphQlRequest(operation);
		}

		@Override
		public GraphQlRequest andRespond(GraphQlResponse response) {
			return addResponse(Flux.just(response), null);
		}

		@Override
		public GraphQlRequest andRespond(Mono<GraphQlResponse> responseMono) {
			return addResponse(Flux.from(responseMono), null);
		}

		@Override
		public GraphQlRequest andRespondWithError(GraphQLError error) {
			return addResponse(Flux.empty(), error);
		}

		@Override
		public GraphQlRequest andStream(Flux<GraphQlResponse> responseFlux) {
			return addResponse(responseFlux, null);
		}

		@Override
		public GraphQlRequest andStreamWithError(Flux<GraphQlResponse> responseFlux, GraphQLError error) {
			return addResponse(responseFlux, error);
		}

		private GraphQlRequest addResponse(Flux<GraphQlResponse> responseFlux, @Nullable GraphQLError error) {
			this.responseFlux = responseFlux;
			this.error = error;
			return this.request;
		}

		public GraphQlRequest getRequest() {
			return this.request;
		}

		public Flux<GraphQlResponse> getResponseFlux() {
			return this.responseFlux;
		}

		@Nullable
		public GraphQLError getError() {
			return this.error;
		}

	}

}

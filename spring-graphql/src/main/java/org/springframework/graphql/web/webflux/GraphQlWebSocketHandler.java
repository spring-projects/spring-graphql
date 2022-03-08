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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import graphql.ExecutionResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.graphql.web.WebSocketInterceptor;
import org.springframework.graphql.web.support.GraphQlMessage;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over
 * WebSocket Protocol</a> and for use in a Spring WebFlux application.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlWebSocketHandler implements WebSocketHandler {

	private static final Log logger = LogFactory.getLog(GraphQlWebSocketHandler.class);

	private static final List<String> SUB_PROTOCOL_LIST = Arrays.asList("graphql-transport-ws", "graphql-ws");


	private final WebGraphQlHandler graphQlHandler;

	private final WebSocketInterceptor webSocketInterceptor;

	private final CodecDelegate codecDelegate;

	private final Duration initTimeoutDuration;


	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over WebSocket requests
	 * @param codecConfigurer codec configurer for JSON encoding and decoding
	 * @param connectionInitTimeout how long to wait after the establishment of
	 * the WebSocket for the {@code "connection_ini"} message from the client.
	 */
	public GraphQlWebSocketHandler(
			WebGraphQlHandler graphQlHandler, CodecConfigurer codecConfigurer, Duration connectionInitTimeout) {

		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");

		this.graphQlHandler = graphQlHandler;
		this.webSocketInterceptor = this.graphQlHandler.webSocketInterceptor();
		this.codecDelegate = new CodecDelegate(codecConfigurer);
		this.initTimeoutDuration = connectionInitTimeout;
	}


	public List<String> getSubProtocols() {
		return SUB_PROTOCOL_LIST;
	}


	@Override
	public Mono<Void> handle(WebSocketSession session) {
		HandshakeInfo handshakeInfo = session.getHandshakeInfo();
		if ("graphql-ws".equalsIgnoreCase(handshakeInfo.getSubProtocol())) {
			if (logger.isDebugEnabled()) {
				logger.debug("apollographql/subscriptions-transport-ws is not supported, nor maintained. "
						+ "Please, use https://github.com/enisdenjo/graphql-ws.");
			}
			return session.close(GraphQlStatus.INVALID_MESSAGE_STATUS);
		}

		// Session state
		AtomicReference<Map<String, Object>> connectionInitPayloadRef = new AtomicReference<>();
		Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

		Mono.delay(this.initTimeoutDuration)
				.then(Mono.defer(() ->
						connectionInitPayloadRef.compareAndSet(null, Collections.emptyMap()) ?
								session.close(GraphQlStatus.INIT_TIMEOUT_STATUS) :
								Mono.empty()))
				.subscribe();

		session.closeStatus()
				.doOnSuccess(closeStatus -> {
					Map<String, Object> connectionInitPayload = connectionInitPayloadRef.get();
					if (connectionInitPayload == null) {
						return;
					}
					int statusCode = (closeStatus != null ? closeStatus.getCode() : 1005);
					this.webSocketInterceptor.handleConnectionClosed(session.getId(), statusCode, connectionInitPayload);
				})
				.subscribe();

		return session.send(session.receive().flatMap(webSocketMessage -> {
			GraphQlMessage message = this.codecDelegate.decode(webSocketMessage);
			String id = message.getId();
			Map<String, Object> payload = message.getPayload();
			switch (message.resolvedType()) {
				case SUBSCRIBE:
					if (connectionInitPayloadRef.get() == null) {
						return GraphQlStatus.close(session, GraphQlStatus.UNAUTHORIZED_STATUS);
					}
					if (id == null) {
						return GraphQlStatus.close(session, GraphQlStatus.INVALID_MESSAGE_STATUS);
					}
					WebInput input = new WebInput(
							handshakeInfo.getUri(), handshakeInfo.getHeaders(), payload, id, null);
					if (logger.isDebugEnabled()) {
						logger.debug("Executing: " + input);
					}
					return this.graphQlHandler.handleRequest(input)
							.flatMapMany((output) -> handleWebOutput(session, id, subscriptions, output))
							.doOnTerminate(() -> subscriptions.remove(id));
				case COMPLETE:
					if (id != null) {
						Subscription subscription = subscriptions.remove(id);
						if (subscription != null) {
							subscription.cancel();
						}
						return this.webSocketInterceptor.handleCancelledSubscription(session.getId(), id)
								.thenMany(Flux.empty());
					}
					return Flux.empty();
				case CONNECTION_INIT:
					if (!connectionInitPayloadRef.compareAndSet(null, payload)) {
						return GraphQlStatus.close(session, GraphQlStatus.TOO_MANY_INIT_REQUESTS_STATUS);
					}
					return this.webSocketInterceptor.handleConnectionInitialization(session.getId(), payload)
							.defaultIfEmpty(Collections.emptyMap())
							.map(ackPayload -> this.codecDelegate.encodeConnectionAck(session, ackPayload))
							.flux()
							.onErrorResume(ex -> GraphQlStatus.close(session, GraphQlStatus.UNAUTHORIZED_STATUS));
				default:
					return GraphQlStatus.close(session, GraphQlStatus.INVALID_MESSAGE_STATUS);
			}
		}));
	}


	@SuppressWarnings("unchecked")
	private Flux<WebSocketMessage> handleWebOutput(WebSocketSession session, String id,
			Map<String, Subscription> subscriptions, WebOutput output) {

		if (logger.isDebugEnabled()) {
			logger.debug("Execution result ready"
					+ (!CollectionUtils.isEmpty(output.getErrors()) ? " with errors: " + output.getErrors() : "")
					+ ".");
		}

		Flux<ExecutionResult> outputFlux;
		if (output.getData() instanceof Publisher) {
			// Subscription
			outputFlux = Flux.from((Publisher<ExecutionResult>) output.getData())
					.doOnSubscribe((subscription) -> {
							Subscription previous = subscriptions.putIfAbsent(id, subscription);
							if (previous != null) {
								throw new SubscriptionExistsException();
							}
					});
		}
		else {
			// Single response (query or mutation)
			outputFlux = (CollectionUtils.isEmpty(output.getErrors()) ? Flux.just(output) :
					Flux.error(new IllegalStateException("Execution failed: " + output.getErrors())));
		}

		return outputFlux
				.map(result -> this.codecDelegate.encodeNext(session, id, result))
				.concatWith(Mono.fromCallable(() -> this.codecDelegate.encodeComplete(session, id)))
				.onErrorResume(ex -> {
						if (ex instanceof SubscriptionExistsException) {
							CloseStatus status = new CloseStatus(4409, "Subscriber for " + id + " already exists");
							return GraphQlStatus.close(session, status);
						}
						return Mono.fromCallable(() -> this.codecDelegate.encodeError(session, id, ex));
				});
	}


	private static class GraphQlStatus {

		static final CloseStatus INVALID_MESSAGE_STATUS = new CloseStatus(4400, "Invalid message");

		static final CloseStatus UNAUTHORIZED_STATUS = new CloseStatus(4401, "Unauthorized");

		static final CloseStatus INIT_TIMEOUT_STATUS = new CloseStatus(4408, "Connection initialisation timeout");

		static final CloseStatus TOO_MANY_INIT_REQUESTS_STATUS = new CloseStatus(4429, "Too many initialisation requests");

		static <V> Flux<V> close(WebSocketSession session, CloseStatus status) {
			return session.close(status).thenMany(Mono.empty());
		}

	}


	@SuppressWarnings("serial")
	private static class SubscriptionExistsException extends RuntimeException {
	}

}

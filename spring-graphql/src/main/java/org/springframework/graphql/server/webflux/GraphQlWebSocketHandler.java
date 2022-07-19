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

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
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

import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.graphql.server.support.GraphQlWebSocketMessage;
import org.springframework.http.HttpHeaders;
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

	private final WebSocketGraphQlInterceptor webSocketInterceptor;

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
		this.webSocketInterceptor = this.graphQlHandler.getWebSocketInterceptor();
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
		WebSocketSessionInfo sessionInfo = new WebFluxSessionInfo(session);
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
					this.webSocketInterceptor.handleConnectionClosed(sessionInfo, statusCode, connectionInitPayload);
				})
				.subscribe();

		return session.send(session.receive().flatMap(webSocketMessage -> {
			GraphQlWebSocketMessage message = this.codecDelegate.decode(webSocketMessage);
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
					WebSocketGraphQlRequest request = new WebSocketGraphQlRequest(
							handshakeInfo.getUri(), handshakeInfo.getHeaders(), payload, id, null, sessionInfo);
					if (logger.isDebugEnabled()) {
						logger.debug("Executing: " + request);
					}
					return this.graphQlHandler.handleRequest(request)
							.flatMapMany(response -> handleResponse(session, id, subscriptions, response))
							.doOnTerminate(() -> subscriptions.remove(id));
				case PING:
					return Flux.just(this.codecDelegate.encode(session, GraphQlWebSocketMessage.pong(null)));
				case COMPLETE:
					if (id != null) {
						Subscription subscription = subscriptions.remove(id);
						if (subscription != null) {
							subscription.cancel();
						}
						return this.webSocketInterceptor.handleCancelledSubscription(sessionInfo, id)
								.thenMany(Flux.empty());
					}
					return Flux.empty();
				case CONNECTION_INIT:
					if (!connectionInitPayloadRef.compareAndSet(null, payload)) {
						return GraphQlStatus.close(session, GraphQlStatus.TOO_MANY_INIT_REQUESTS_STATUS);
					}
					return this.webSocketInterceptor.handleConnectionInitialization(sessionInfo, payload)
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
	private Flux<WebSocketMessage> handleResponse(WebSocketSession session, String id,
			Map<String, Subscription> subscriptions, WebGraphQlResponse response) {

		if (logger.isDebugEnabled()) {
			logger.debug("Execution result ready"
					+ (!CollectionUtils.isEmpty(response.getErrors()) ? " with errors: " + response.getErrors() : "")
					+ ".");
		}

		Flux<Map<String, Object>> responseFlux;
		if (response.getData() instanceof Publisher) {
			// Subscription
			responseFlux = Flux.from((Publisher<ExecutionResult>) response.getData())
					.map(ExecutionResult::toSpecification)
					.doOnSubscribe((subscription) -> {
							Subscription previous = subscriptions.putIfAbsent(id, subscription);
							if (previous != null) {
								throw new SubscriptionExistsException();
							}
					});
		}
		else {
			// Single response (query or mutation) that may contain errors
			responseFlux = Flux.just(response.toMap());
		}

		return responseFlux
				.map(responseMap -> this.codecDelegate.encodeNext(session, id, responseMap))
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


	private static class WebFluxSessionInfo implements WebSocketSessionInfo {

		private final WebSocketSession session;

		private WebFluxSessionInfo(WebSocketSession session) {
			this.session = session;
		}

		@Override
		public String getId() {
			return this.session.getId();
		}

		@Override
		public Map<String, Object> getAttributes() {
			return this.session.getAttributes();
		}

		@Override
		public URI getUri() {
			return this.session.getHandshakeInfo().getUri();
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.session.getHandshakeInfo().getHeaders();
		}

		@Override
		public Mono<Principal> getPrincipal() {
			return this.session.getHandshakeInfo().getPrincipal();
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return this.session.getHandshakeInfo().getRemoteAddress();
		}
	}


	@SuppressWarnings("serial")
	private static class SubscriptionExistsException extends RuntimeException {
	}



}

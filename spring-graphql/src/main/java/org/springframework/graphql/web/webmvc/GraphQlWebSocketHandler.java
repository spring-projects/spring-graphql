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

package org.springframework.graphql.web.webmvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.graphql.web.WebSocketInterceptor;
import org.springframework.graphql.web.support.GraphQlMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ExceptionWebSocketHandlerDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over
 * WebSocket Protocol</a> and for use on a Servlet container with
 * {@code spring-websocket}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

	private static final Log logger = LogFactory.getLog(GraphQlWebSocketHandler.class);

	private static final List<String> SUB_PROTOCOL_LIST = Arrays.asList("graphql-transport-ws", "graphql-ws");


	private final WebGraphQlHandler graphQlHandler;

	private final WebSocketInterceptor webSocketInterceptor;

	private final Duration initTimeoutDuration;

	private final HttpMessageConverter<?> converter;

	private final Map<String, SessionState> sessionInfoMap = new ConcurrentHashMap<>();

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over WebSocket requests
	 * @param converter for JSON encoding and decoding
	 * @param connectionInitTimeout how long to wait after the establishment of
	 * the WebSocket for the {@code "connection_ini"} message from the client.
	 */
	public GraphQlWebSocketHandler(
			WebGraphQlHandler graphQlHandler, HttpMessageConverter<?> converter, Duration connectionInitTimeout) {

		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		Assert.notNull(converter, "HttpMessageConverter for JSON is required");

		this.graphQlHandler = graphQlHandler;
		this.webSocketInterceptor = this.graphQlHandler.webSocketInterceptor();
		this.initTimeoutDuration = connectionInitTimeout;
		this.converter = converter;
	}

	@Override
	public List<String> getSubProtocols() {
		return SUB_PROTOCOL_LIST;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		if ("graphql-ws".equalsIgnoreCase(session.getAcceptedProtocol())) {
			if (logger.isDebugEnabled()) {
				logger.debug("apollographql/subscriptions-transport-ws is not supported, nor maintained. "
						+ "Please, use https://github.com/enisdenjo/graphql-ws.");
			}
			GraphQlStatus.closeSession(session, GraphQlStatus.INVALID_MESSAGE_STATUS);
			return;
		}

		SessionState sessionState = new SessionState(session.getId());
		this.sessionInfoMap.put(session.getId(), sessionState);

		Mono.delay(this.initTimeoutDuration)
				.then(Mono.fromRunnable(() -> {
						if (sessionState.setConnectionInitPayload(Collections.emptyMap())) {
							GraphQlStatus.closeSession(session, GraphQlStatus.INIT_TIMEOUT_STATUS);
						}
				}))
				.subscribe();

	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage webSocketMessage) throws Exception {
		GraphQlMessage message = decode(webSocketMessage);
		String id = message.getId();
		Map<String, Object> payload = message.getPayload();
		SessionState sessionState = getSessionInfo(session);
		switch (message.resolvedType()) {
			case SUBSCRIBE:
				if (sessionState.getConnectionInitPayload() == null) {
					GraphQlStatus.closeSession(session, GraphQlStatus.UNAUTHORIZED_STATUS);
					return;
				}
				if (id == null) {
					GraphQlStatus.closeSession(session, GraphQlStatus.INVALID_MESSAGE_STATUS);
					return;
				}
				URI uri = session.getUri();
				Assert.notNull(uri, "Expected handshake url");
				HttpHeaders headers = session.getHandshakeHeaders();
				WebInput input = new WebInput(uri, headers, payload, id, null);
				if (logger.isDebugEnabled()) {
					logger.debug("Executing: " + input);
				}
				this.graphQlHandler.handleRequest(input)
						.flatMapMany((output) -> handleWebOutput(session, input.getId(), output))
						.publishOn(sessionState.getScheduler()) // Serial blocking send via single thread
						.subscribe(new SendMessageSubscriber(id, session, sessionState));
				return;
			case PING:
				session.sendMessage(encode(GraphQlMessage.pong(null)));
				return;
			case COMPLETE:
				if (id != null) {
					Subscription subscription = sessionState.getSubscriptions().remove(id);
					if (subscription != null) {
						subscription.cancel();
					}
					this.webSocketInterceptor.handleCancelledSubscription(session.getId(), id)
							.block(Duration.ofSeconds(10));
				}
				return;
			case CONNECTION_INIT:
				if (!sessionState.setConnectionInitPayload(payload)) {
					GraphQlStatus.closeSession(session, GraphQlStatus.TOO_MANY_INIT_REQUESTS_STATUS);
					return;
				}
				this.webSocketInterceptor.handleConnectionInitialization(session.getId(), payload)
						.defaultIfEmpty(Collections.emptyMap())
						.publishOn(sessionState.getScheduler()) // Serial blocking send via single thread
						.doOnNext(ackPayload -> {
							TextMessage outputMessage = encode(GraphQlMessage.connectionAck(ackPayload));
							try {
								session.sendMessage(outputMessage);
							}
							catch (IOException ex) {
								throw new IllegalStateException(ex);
							}
						})
						.onErrorResume(ex -> {
							GraphQlStatus.closeSession(session, GraphQlStatus.UNAUTHORIZED_STATUS);
							return Mono.empty();
						})
						.block(Duration.ofSeconds(10));
				return;
			default:
				GraphQlStatus.closeSession(session, GraphQlStatus.INVALID_MESSAGE_STATUS);
		}
	}

	@SuppressWarnings("unchecked")
	private GraphQlMessage decode(TextMessage message) throws IOException {
		return ((GenericHttpMessageConverter<GraphQlMessage>) this.converter)
				.read(GraphQlMessage.class, null, new HttpInputMessageAdapter(message));
	}

	private SessionState getSessionInfo(WebSocketSession session) {
		SessionState info = this.sessionInfoMap.get(session.getId());
		Assert.notNull(info, "No SessionInfo for " + session);
		return info;
	}

	@SuppressWarnings("unchecked")
	private Flux<TextMessage> handleWebOutput(WebSocketSession session, String id, WebOutput output) {
		if (logger.isDebugEnabled()) {
			logger.debug("Execution result ready"
					+ (!CollectionUtils.isEmpty(output.getErrors()) ? " with errors: " + output.getErrors() : "")
					+ ".");
		}
		Flux<Map<String, Object>> responseFlux;
		if (output.getData() instanceof Publisher) {
			// Subscription
			responseFlux = Flux.from((Publisher<ExecutionResult>) output.getData())
					.map(ExecutionResult::toSpecification)
					.doOnSubscribe((subscription) -> {
							Subscription prev = getSessionInfo(session).getSubscriptions().putIfAbsent(id, subscription);
							if (prev != null) {
								throw new SubscriptionExistsException();
							}
					});
		}
		else {
			// Single response (query or mutation) that may contain errors
			responseFlux = Flux.just(output.toMap());
		}

		return responseFlux
				.map(responseMap -> encode(GraphQlMessage.next(id, responseMap)))
				.concatWith(Mono.fromCallable(() -> encode(GraphQlMessage.complete(id))))
				.onErrorResume((ex) -> {
						if (ex instanceof SubscriptionExistsException) {
							CloseStatus status = new CloseStatus(4409, "Subscriber for " + id + " already exists");
							GraphQlStatus.closeSession(session, status);
							return Flux.empty();
						}
						String message = ex.getMessage();
						GraphQLError error = GraphqlErrorBuilder.newError().message(message).build();
						return Mono.just(encode(GraphQlMessage.error(id, error)));
				});
	}

	@SuppressWarnings("unchecked")
	private <T> TextMessage encode(GraphQlMessage message) {
		try {
			HttpOutputMessageAdapter outputMessage = new HttpOutputMessageAdapter();
			((HttpMessageConverter<T>) this.converter).write((T) message, null, outputMessage);
			return new TextMessage(outputMessage.toByteArray());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write " + message + " as JSON", ex);
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		SessionState info = this.sessionInfoMap.remove(session.getId());
		if (info != null) {
			info.dispose();
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
		String id = session.getId();
		SessionState info = this.sessionInfoMap.remove(id);
		if (info != null) {
			info.dispose();
			Map<String, Object> connectionInitPayload = info.getConnectionInitPayload();
			if (connectionInitPayload != null) {
				this.webSocketInterceptor.handleConnectionClosed(id, closeStatus.getCode(), connectionInitPayload);
			}
		}
	}

	@Override
	public boolean supportsPartialMessages() {
		return false;
	}


	private static class GraphQlStatus {

		private static final CloseStatus INVALID_MESSAGE_STATUS = new CloseStatus(4400, "Invalid message");

		private static final CloseStatus UNAUTHORIZED_STATUS = new CloseStatus(4401, "Unauthorized");

		private static final CloseStatus INIT_TIMEOUT_STATUS = new CloseStatus(4408, "Connection initialisation timeout");

		private static final CloseStatus TOO_MANY_INIT_REQUESTS_STATUS = new CloseStatus(4429, "Too many initialisation requests");

		static void closeSession(WebSocketSession session, CloseStatus status) {
			try {
				session.close(status);
			}
			catch (IOException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Error while closing session with status: " + status, ex);
				}
			}
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

	private static class HttpOutputMessageAdapter extends ByteArrayOutputStream implements HttpOutputMessage {

		private static final HttpHeaders noOpHeaders = new HttpHeaders();

		@Override
		public OutputStream getBody() {
			return this;
		}

		@Override
		public HttpHeaders getHeaders() {
			return noOpHeaders;
		}

	}

	private static class SessionState {

		private final AtomicReference<Map<String, Object>> connectionInitPayloadRef = new AtomicReference<>();

		private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

		private final Scheduler scheduler;

		SessionState(String sessionId) {
			this.scheduler = Schedulers.newSingle("GraphQL-WsSession-" + sessionId);
		}

		@Nullable
		Map<String, Object> getConnectionInitPayload() {
			return this.connectionInitPayloadRef.get();
		}

		boolean setConnectionInitPayload(Map<String, Object> payload) {
			return this.connectionInitPayloadRef.compareAndSet(null, payload);
		}


		Map<String, Subscription> getSubscriptions() {
			return this.subscriptions;
		}

		void dispose() {
			for (Map.Entry<String, Subscription> entry : this.subscriptions.entrySet()) {
				try {
					entry.getValue().cancel();
				}
				catch (Throwable ex) {
					// Ignore and keep on
				}
			}
			this.subscriptions.clear();
			this.scheduler.dispose();
		}

		Scheduler getScheduler() {
			return this.scheduler;
		}

	}

	private static class SendMessageSubscriber extends BaseSubscriber<TextMessage> {

		private final String subscriptionId;

		private final WebSocketSession session;

		private final SessionState sessionState;

		SendMessageSubscriber(String subscriptionId, WebSocketSession session, SessionState sessionState) {
			this.subscriptionId = subscriptionId;
			this.session = session;
			this.sessionState = sessionState;
		}

		@Override
		protected void hookOnSubscribe(Subscription subscription) {
			subscription.request(1);
		}

		@Override
		protected void hookOnNext(TextMessage nextMessage) {
			try {
				this.session.sendMessage(nextMessage);
				request(1);
			}
			catch (IOException ex) {
				ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.session, ex, logger);
			}
		}

		@Override
		public void hookOnError(Throwable ex) {
			ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.session, ex, logger);
		}

		@Override
		public void hookOnComplete() {
			this.sessionState.getSubscriptions().remove(this.subscriptionId);
		}

	}

	@SuppressWarnings("serial")
	private static class SubscriptionExistsException extends RuntimeException {
	}

}

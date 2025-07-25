/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.server.webmvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.graphql.server.support.GraphQlWebSocketMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ExceptionWebSocketHandlerDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over
 * WebSocket Protocol</a> and for use on a Servlet container with
 * {@code spring-websocket}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@RegisterReflectionForBinding(GraphQlWebSocketMessage.class)
public class GraphQlWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

	private static final Log logger = LogFactory.getLog(GraphQlWebSocketHandler.class);

	private static final List<String> SUB_PROTOCOL_LIST = Arrays.asList("graphql-transport-ws", "graphql-ws");


	private final WebGraphQlHandler graphQlHandler;

	private final ContextHandshakeInterceptor contextHandshakeInterceptor;

	private final WebSocketGraphQlInterceptor webSocketGraphQlInterceptor;

	private final Duration initTimeoutDuration;

	private final HttpMessageConverter<Object> converter;

	private final @Nullable Duration keepAliveDuration;

	private final Map<String, SessionState> sessionInfoMap = new ConcurrentHashMap<>();


	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over WebSocket requests
	 * @param converter for JSON encoding and decoding
	 * @param connectionInitTimeout how long to wait after the establishment of
	 * the WebSocket for the {@code "connection_ini"} message from the client.
	 */
	public GraphQlWebSocketHandler(
			WebGraphQlHandler graphQlHandler, HttpMessageConverter<Object> converter, Duration connectionInitTimeout) {

		this(graphQlHandler, converter, connectionInitTimeout, null);
	}

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over WebSocket requests
	 * @param converter for JSON encoding and decoding
	 * @param connectionInitTimeout how long to wait after the establishment of
	 * the WebSocket for the {@code "connection_ini"} message from the client.
	 * @param keepAliveDuration how frequently to send ping messages when no
	 * other messages are sent
	 * @since 1.3
	 */
	public GraphQlWebSocketHandler(
			WebGraphQlHandler graphQlHandler, HttpMessageConverter<Object> converter,
			Duration connectionInitTimeout, @Nullable Duration keepAliveDuration) {

		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		Assert.notNull(converter, "HttpMessageConverter for JSON is required");

		this.graphQlHandler = graphQlHandler;
		this.contextHandshakeInterceptor = new ContextHandshakeInterceptor(graphQlHandler.contextSnapshotFactory());
		this.webSocketGraphQlInterceptor = this.graphQlHandler.getWebSocketInterceptor();
		this.initTimeoutDuration = connectionInitTimeout;
		this.converter = converter;
		this.keepAliveDuration = keepAliveDuration;
	}


	@Override
	public List<String> getSubProtocols() {
		return SUB_PROTOCOL_LIST;
	}

	/**
	 * Initialize a {@link WebSocketHttpRequestHandler} that wraps this instance
	 * and also inserts a {@link HandshakeInterceptor} for context propagation.
	 * @param handshakeHandler the handler for WebSocket handshake
	 * @since 1.1.0
	 */
	public WebSocketHttpRequestHandler initWebSocketHttpRequestHandler(HandshakeHandler handshakeHandler) {
		WebSocketHttpRequestHandler handler = new WebSocketHttpRequestHandler(this, handshakeHandler);
		handler.setHandshakeInterceptors(Collections.singletonList(this.contextHandshakeInterceptor));
		return handler;
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

		SessionState sessionState = new SessionState(session.getId(), new WebMvcSessionInfo(session));
		this.sessionInfoMap.put(session.getId(), sessionState);

		Mono.delay(this.initTimeoutDuration)
				.then(Mono.fromRunnable(() -> {
						if (sessionState.setConnectionInitPayload(Collections.emptyMap())) {
							GraphQlStatus.closeSession(session, GraphQlStatus.INIT_TIMEOUT_STATUS);
						}
				}))
				.subscribe();

	}

	@SuppressWarnings("try")
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage webSocketMessage) throws Exception {
		try (AutoCloseable closeable = ContextHandshakeInterceptor.setThreadLocals(session)) {
			handleInternal(session, webSocketMessage);
		}
	}

	private void handleInternal(WebSocketSession session, TextMessage webSocketMessage) throws IOException {
		GraphQlWebSocketMessage message = decode(webSocketMessage);
		String id = message.getId();
		Map<String, Object> payload = message.getPayload();
		SessionState state = getSessionInfo(session);
		switch (message.resolvedType()) {
			case SUBSCRIBE -> {
				if (state.getConnectionInitPayload() == null) {
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
				WebSocketGraphQlRequest request = new WebSocketGraphQlRequest(
						uri, headers, null, session.getRemoteAddress(), session.getAttributes(), payload, id, null, state.getSessionInfo());
				if (logger.isDebugEnabled()) {
					logger.debug("Executing: " + request);
				}
				this.graphQlHandler.handleRequest(request)
						.flatMapMany((response) -> handleResponse(session, request.getId(), response))
						.publishOn(state.getScheduler()) // Serial blocking send via single thread
						.subscribe(new SendMessageSubscriber(id, session, state));
			}
			case PING ->
					state.getScheduler().schedule(() -> {
						try {
							session.sendMessage(encode(GraphQlWebSocketMessage.pong(null)));
						}
						catch (IOException ex) {
							ExceptionWebSocketHandlerDecorator.tryCloseWithError(session, ex, logger);
						}
					});
			case PONG -> {
			}
			case COMPLETE -> {
				if (id != null) {
					Subscription subscription = state.getSubscriptions().remove(id);
					if (subscription != null) {
						subscription.cancel();
					}
					this.webSocketGraphQlInterceptor.handleCancelledSubscription(state.getSessionInfo(), id)
							.block(Duration.ofSeconds(10));
				}
			}
			case CONNECTION_INIT -> {
				if (!state.setConnectionInitPayload(payload)) {
					GraphQlStatus.closeSession(session, GraphQlStatus.TOO_MANY_INIT_REQUESTS_STATUS);
					return;
				}
				this.webSocketGraphQlInterceptor.handleConnectionInitialization(state.getSessionInfo(), payload)
						.defaultIfEmpty(Collections.emptyMap())
						.publishOn(state.getScheduler()) // Serial blocking send via single thread
						.doOnNext((ackPayload) -> {
							TextMessage outputMessage = encode(GraphQlWebSocketMessage.connectionAck(ackPayload));
							try {
								session.sendMessage(outputMessage);
							}
							catch (IOException ex) {
								throw new IllegalStateException(ex);
							}
						})
						.onErrorResume((ex) -> {
							GraphQlStatus.closeSession(session, GraphQlStatus.UNAUTHORIZED_STATUS);
							return Mono.empty();
						})
						.block(Duration.ofSeconds(10));

				if (this.keepAliveDuration != null) {
					Flux.interval(this.keepAliveDuration, this.keepAliveDuration)
							.filter((aLong) -> true)
							.publishOn(state.getScheduler()) // Serial blocking send via single thread
							.doOnNext((aLong) -> {
								try {
									session.sendMessage(encode(GraphQlWebSocketMessage.ping(null)));
								}
								catch (IOException ex) {
									ExceptionWebSocketHandlerDecorator.tryCloseWithError(session, ex, logger);
								}
							})
							.subscribe(state.getKeepAliveSubscriber());
				}
			}
			default -> GraphQlStatus.closeSession(session, GraphQlStatus.INVALID_MESSAGE_STATUS);
		}
	}

	private GraphQlWebSocketMessage decode(TextMessage message) throws IOException {
		return (GraphQlWebSocketMessage) this.converter
				.read(GraphQlWebSocketMessage.class, new HttpInputMessageAdapter(message));
	}

	private SessionState getSessionInfo(WebSocketSession session) {
		SessionState info = this.sessionInfoMap.get(session.getId());
		Assert.notNull(info, "No SessionInfo for " + session);
		return info;
	}

	@SuppressWarnings("unchecked")
	private Flux<TextMessage> handleResponse(WebSocketSession session, String id, WebGraphQlResponse response) {
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
							Subscription prev = getSessionInfo(session).getSubscriptions().putIfAbsent(id, subscription);
							if (prev != null) {
								throw new SubscriptionExistsException();
							}
					});
		}
		else {
			// Single response (query or mutation) that may contain errors
			responseFlux = Flux.just(response.toMap());
		}

		return responseFlux
				.map((responseMap) -> encode(GraphQlWebSocketMessage.next(id, responseMap)))
				.concatWith(Mono.fromCallable(() -> encode(GraphQlWebSocketMessage.complete(id))))
				.onErrorResume((ex) -> {
					if (ex instanceof SubscriptionExistsException) {
						CloseStatus status = new CloseStatus(4409, "Subscriber for " + id + " already exists");
						GraphQlStatus.closeSession(session, status);
						return Flux.empty();
					}
					List<GraphQLError> errors;
					if (ex instanceof SubscriptionPublisherException subscriptionEx) {
						errors = subscriptionEx.getErrors();
					}
					else {
						if (logger.isErrorEnabled()) {
							logger.error("Unresolved " + ex.getClass().getSimpleName() + " for request id " + id, ex);
						}
						errors = Collections.singletonList(GraphqlErrorBuilder.newError()
								.message("Subscription error")
								.errorType(ErrorType.INTERNAL_ERROR)
								.build());
					}
					return Mono.just(encode(GraphQlWebSocketMessage.error(id, errors)));
				});
	}

	@SuppressWarnings("unchecked")
	private <T> TextMessage encode(GraphQlWebSocketMessage message) {
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
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
		String id = session.getId();
		SessionState state = this.sessionInfoMap.remove(id);
		if (state != null) {
			state.dispose();
			Map<String, Object> connectionInitPayload = state.getConnectionInitPayload();
			if (connectionInitPayload != null) {
				this.webSocketGraphQlInterceptor.handleConnectionClosed(
						state.getSessionInfo(), closeStatus.getCode(), connectionInitPayload);
			}
		}
	}


	/**
	 * {@code HandshakeInterceptor} that propagates ThreadLocal context through
	 * the attributes map in {@code WebSocketSession}.
	 */
	private static final class ContextHandshakeInterceptor implements HandshakeInterceptor {

		private static final String KEY = ContextSnapshot.class.getName();

		private final ContextSnapshotFactory snapshotFactory;

		ContextHandshakeInterceptor(ContextSnapshotFactory factory) {
			this.snapshotFactory = factory;
		}

		@Override
		public boolean beforeHandshake(
				ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
				Map<String, Object> attributes) {

			attributes.put(KEY, this.snapshotFactory.captureAll());
			return true;
		}

		@Override
		public void afterHandshake(
				ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
				@Nullable Exception exception) {
		}

		static AutoCloseable setThreadLocals(WebSocketSession session) {
			ContextSnapshot snapshot = (ContextSnapshot) session.getAttributes().get(KEY);
			Assert.notNull(snapshot, "Expected ContextSnapshot in WebSocketSession attributes");
			return snapshot.setThreadLocals();
		}
	}


	private static final class GraphQlStatus {

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

	private static final class HttpOutputMessageAdapter extends ByteArrayOutputStream implements HttpOutputMessage {

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

		private final WebSocketSessionInfo sessionInfo;

		private final AtomicReference<Map<String, Object>> connectionInitPayloadRef = new AtomicReference<>();

		private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

		private final Scheduler scheduler;

		private final KeepAliveSubscriber keepAliveSubscriber;

		SessionState(String graphQlSessionId, WebMvcSessionInfo sessionInfo) {
			this.sessionInfo = sessionInfo;
			this.scheduler = Schedulers.newSingle("GraphQL-WsSession-" + graphQlSessionId);
			this.keepAliveSubscriber = new KeepAliveSubscriber(sessionInfo.getSession());
		}

		WebSocketSessionInfo getSessionInfo() {
			return this.sessionInfo;
		}

		@Nullable Map<String, Object> getConnectionInitPayload() {
			return this.connectionInitPayloadRef.get();
		}

		boolean setConnectionInitPayload(Map<String, Object> payload) {
			return this.connectionInitPayloadRef.compareAndSet(null, payload);
		}

		KeepAliveSubscriber getKeepAliveSubscriber() {
			return this.keepAliveSubscriber;
		}

		Map<String, Subscription> getSubscriptions() {
			return this.subscriptions;
		}

		void dispose() {
			this.keepAliveSubscriber.cancel();
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


	private static final class WebMvcSessionInfo implements WebSocketSessionInfo {

		private final WebSocketSession session;

		private WebMvcSessionInfo(WebSocketSession session) {
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
			URI uri = this.session.getUri();
			Assert.notNull(uri, "Expected URI");
			return uri;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.session.getHandshakeHeaders();
		}

		@Override
		public Mono<Principal> getPrincipal() {
			return Mono.justOrEmpty(this.session.getPrincipal());
		}

		@Override
		public @Nullable InetSocketAddress getRemoteAddress() {
			return this.session.getRemoteAddress();
		}

		WebSocketSession getSession() {
			return this.session;
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
				cancel();
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


	private static class KeepAliveSubscriber extends BaseSubscriber<Long> {

		private final WebSocketSession session;

		KeepAliveSubscriber(WebSocketSession session) {
			this.session = session;
		}

		@Override
		protected void hookOnSubscribe(Subscription subscription) {
			subscription.request(Integer.MAX_VALUE);
		}

		@Override
		public void hookOnError(Throwable ex) {
			ExceptionWebSocketHandlerDecorator.tryCloseWithError(this.session, ex, logger);
		}
	}


	@SuppressWarnings("serial")
	private static final class SubscriptionExistsException extends RuntimeException {
	}

}

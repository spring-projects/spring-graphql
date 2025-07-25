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

package org.springframework.graphql.client;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.support.GraphQlWebSocketMessage;
import org.springframework.graphql.server.support.GraphQlWebSocketMessageType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

/**
 * {@link GraphQlTransport} for GraphQL over WebSocket via {@link WebSocketClient}.
 *
 * @author Rossen Stoyanchev
 * @see <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL over WebSocket protocol</a>
 */
final class WebSocketGraphQlTransport implements GraphQlTransport {

	private static final Log logger = LogFactory.getLog(WebSocketGraphQlTransport.class);

	private final URI url;

	private final HttpHeaders headers = new HttpHeaders();

	private final WebSocketClient webSocketClient;

	private final GraphQlSessionHandler graphQlSessionHandler;

	private final Mono<GraphQlSession> graphQlSessionMono;

	private final @Nullable Duration keepAlive;


	WebSocketGraphQlTransport(
			URI url, @Nullable HttpHeaders headers, WebSocketClient client, CodecConfigurer codecConfigurer,
			WebSocketGraphQlClientInterceptor interceptor, @Nullable Duration keepAlive) {

		Assert.notNull(url, "URI is required");
		Assert.notNull(client, "WebSocketClient is required");
		Assert.notNull(codecConfigurer, "CodecConfigurer is required");
		Assert.notNull(interceptor, "WebSocketGraphQlClientInterceptor is required");

		this.url = url;
		this.headers.putAll((headers != null) ? headers : HttpHeaders.EMPTY);
		this.webSocketClient = client;
		this.keepAlive = keepAlive;

		this.graphQlSessionHandler = new GraphQlSessionHandler(codecConfigurer, interceptor, keepAlive);

		this.graphQlSessionMono = initGraphQlSession(this.url, this.headers, client, this.graphQlSessionHandler)
				.cacheInvalidateWhen(GraphQlSession::notifyWhenClosed);
	}

	@SuppressWarnings({"CallingSubscribeInNonBlockingScope", "ReactorTransformationOnMonoVoid"})
	private static Mono<GraphQlSession> initGraphQlSession(
			URI uri, HttpHeaders headers, WebSocketClient client, GraphQlSessionHandler handler) {

		return Mono.defer(() -> {
			if (handler.isStopped()) {
				return Mono.error(new IllegalStateException("WebSocketGraphQlTransport has been stopped"));
			}

			// Get the session Mono before connecting
			Mono<GraphQlSession> sessionMono = handler.getGraphQlSession();

			client.execute(uri, headers, handler)
					.subscribe((aVoid) -> {

							},
							handler::handleWebSocketSessionError,
							handler::handleWebSocketSessionClosed);

			return sessionMono;
		});
	}


	URI getUrl() {
		return this.url;
	}

	HttpHeaders getHeaders() {
		return this.headers;
	}

	WebSocketClient getWebSocketClient() {
		return this.webSocketClient;
	}

	CodecConfigurer getCodecConfigurer() {
		return this.graphQlSessionHandler.getCodecConfigurer();
	}


	/**
	 * Start the transport by connecting the WebSocket, sending the
	 * "connection_init" and waiting for the "connection_ack" message.
	 * @return {@code Mono} that completes when the WebSocket is connected and
	 * ready to begin sending GraphQL requests
	 */
	Mono<Void> start() {
		this.graphQlSessionHandler.setStopped(false);
		return this.graphQlSessionMono.then();
	}

	/**
	 * Stop the transport by closing the WebSocket with
	 * {@link org.springframework.web.reactive.socket.CloseStatus#NORMAL} and
	 * terminating in-progress requests with an error signal.
	 * <p>New requests are rejected from the time of this call. If necessary,
	 * call {@link #start()} to allow requests again.
	 * @return {@code Mono} that completes when the underlying session is closed
	 */
	Mono<Void> stop() {
		this.graphQlSessionHandler.setStopped(true);
		return this.graphQlSessionMono.flatMap(GraphQlSession::close).onErrorResume((ex) -> Mono.empty());
	}

	@Override
	public Mono<GraphQlResponse> execute(GraphQlRequest request) {
		return this.graphQlSessionMono.flatMap((session) -> session.execute(request));
	}

	@Override
	public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
		return this.graphQlSessionMono.flatMapMany((session) -> session.executeSubscription(request));
	}

	@Nullable Duration getKeepAlive() {
		return this.keepAlive;
	}


	/**
	 * Client {@code WebSocketHandler} for GraphQL that deals with WebSocket
	 * concerns such as encoding and decoding of messages, {@link GraphQlSession}
	 * initialization and lifecycle, as well the lifecycle of the WebSocket.
	 *
	 * <p>This handler is for use as a singleton, but expects only one shared
	 * connection at a time. This is managed at a higher level by caching and
	 * re-using the {@link #getGraphQlSession() GraphQlSession} until closed at
	 * which point a new connection can be started.
	 */
	private static class GraphQlSessionHandler implements WebSocketHandler {

		private final CodecDelegate codecDelegate;

		private final WebSocketGraphQlClientInterceptor interceptor;

		private Sinks.One<GraphQlSession> graphQlSessionSink;

		private final AtomicBoolean stopped = new AtomicBoolean();

		private final @Nullable Duration keepAlive;


		GraphQlSessionHandler(
				CodecConfigurer codecConfigurer, WebSocketGraphQlClientInterceptor interceptor,
				@Nullable Duration keepAlive) {

			this.codecDelegate = new CodecDelegate(codecConfigurer);
			this.interceptor = interceptor;
			this.graphQlSessionSink = Sinks.unsafe().one();
			this.keepAlive = keepAlive;
		}


		CodecConfigurer getCodecConfigurer() {
			return this.codecDelegate.getCodecConfigurer();
		}


		@Override
		public List<String> getSubProtocols() {
			return Collections.singletonList("graphql-transport-ws");
		}

		/**
		 * Return the {@link GraphQlSession} for sending requests.
		 * The {@code Mono} completes when the WebSocket session is connected and
		 * the "connection_init" and "connection_ack" messages are exchanged or
		 * returns an error if it fails for any reason.
		 */
		Mono<GraphQlSession> getGraphQlSession() {
			return this.graphQlSessionSink.asMono();
		}

		/**
		 * When the handler is marked "stopped", i.e. set to {@code true}, new
		 * requests are rejected. When set to {@code true} they are allowed.
		 */
		void setStopped(boolean stopped) {
			this.stopped.set(stopped);
		}

		/**
		 * Whether the handler is marked {@link #setStopped(boolean) "stopped"}.
		 */
		boolean isStopped() {
			return this.stopped.get();
		}


		@SuppressWarnings({"ReactorZipWithMonoVoid", "ReactiveStreamsThrowInOperator"})
		@Override
		public Mono<Void> handle(WebSocketSession session) {

			Assert.state(sessionNotInitialized(),
					"This handler supports only one session at a time, for shared use.");

			GraphQlSession graphQlSession = new GraphQlSession(session);
			registerCloseStatusHandling(graphQlSession, session);

			Mono<GraphQlWebSocketMessage> connectionInitMono = this.interceptor.connectionInitPayload()
					.defaultIfEmpty(Collections.emptyMap())
					.map(GraphQlWebSocketMessage::connectionInit);

			Mono<Void> sendCompletion =
					session.send(connectionInitMono.concatWith(graphQlSession.getRequestFlux())
							.map((message) -> this.codecDelegate.encode(session, message)));

			Mono<Void> receiveCompletion = session.receive()
					.flatMap((webSocketMessage) -> {
						if (sessionNotInitialized()) {
							try {
								GraphQlWebSocketMessage message = this.codecDelegate.decode(webSocketMessage);
								Assert.notNull(message, () -> "Cannot decode graphql message from: " + webSocketMessage);
								Assert.state(message.resolvedType() == GraphQlWebSocketMessageType.CONNECTION_ACK,
										() -> "Unexpected message before connection_ack: " + message);
								return this.interceptor.handleConnectionAck(message.getPayload())
										.then(Mono.defer(() -> {
											if (logger.isDebugEnabled()) {
												logger.debug(graphQlSession + " initialized");
											}
											Sinks.EmitResult result = this.graphQlSessionSink.tryEmitValue(graphQlSession);
											if (result.isFailure()) {
												return Mono.error(new IllegalStateException(
														"GraphQlSession initialized but could not be emitted: " + result));
											}
											return Mono.empty();
										}));
							}
							catch (Throwable ex) {
								this.graphQlSessionSink.tryEmitError(ex);
								return Mono.error(ex);
							}
						}
						else {
							try {
								GraphQlWebSocketMessage message = this.codecDelegate.decode(webSocketMessage);
								if (message == null) {
									throw new IllegalStateException("Cannot decode graphql message from: " + webSocketMessage);
								}
								switch (message.resolvedType()) {
									case NEXT -> graphQlSession.handleNext(message);
									case PING -> graphQlSession.sendPong(null);
									case PONG -> { }
									case ERROR -> graphQlSession.handleError(message);
									case COMPLETE -> graphQlSession.handleComplete(message);
									default -> throw new IllegalStateException(
											"Unexpected message type: '" + message.getType() + "'");
								}
							}
							catch (Throwable ex) {
								if (logger.isErrorEnabled()) {
									logger.error("Closing " + session + ": " + ex);
								}
								return session.close(new CloseStatus(4400, "Invalid message"));
							}
						}
						return Mono.empty();
					})
					.mergeWith((this.keepAlive != null) ?
							Flux.interval(this.keepAlive, this.keepAlive)
									.filter((aLong) -> graphQlSession.checkSentOrReceivedMessagesAndClear())
									.doOnNext((aLong) -> graphQlSession.sendPing())
									.then() :
							Flux.empty())
					.then();

			if (this.keepAlive != null) {
				Flux.interval(this.keepAlive, this.keepAlive)
						.filter((aLong) -> graphQlSession.checkSentOrReceivedMessagesAndClear())
						.doOnNext((aLong) -> graphQlSession.sendPing())
						.subscribe();
			}

			return Mono.zip(sendCompletion, receiveCompletion.then()).then();
		}

		private boolean sessionNotInitialized() {
			return !Boolean.TRUE.equals(this.graphQlSessionSink.scan(Scannable.Attr.TERMINATED));
		}

		private void registerCloseStatusHandling(GraphQlSession graphQlSession, WebSocketSession session) {
			session.closeStatus()
					.defaultIfEmpty(CloseStatus.NO_STATUS_CODE)
					.doOnNext((closeStatus) -> {
						String closeStatusMessage = initCloseStatusMessage(closeStatus, null, graphQlSession);
						if (logger.isDebugEnabled()) {
							logger.debug(closeStatusMessage);
						}
						terminateGraphQlSession(graphQlSession, closeStatus, closeStatusMessage, null);
					})
					.doOnError((cause) -> {
						CloseStatus closeStatus = CloseStatus.NO_STATUS_CODE;
						String closeStatusMessage = initCloseStatusMessage(closeStatus, cause, graphQlSession);
						if (logger.isErrorEnabled()) {
							logger.error(closeStatusMessage);
						}
						terminateGraphQlSession(graphQlSession, closeStatus, closeStatusMessage, cause);
					})
					.subscribe();
		}

		private void terminateGraphQlSession(
				GraphQlSession session, CloseStatus closeStatus, String closeStatusMessage, @Nullable Throwable cause) {

			if (sessionNotInitialized()) {
				this.graphQlSessionSink.tryEmitError(new IllegalStateException(closeStatusMessage, cause));
				this.graphQlSessionSink = Sinks.unsafe().one();
			}
			session.terminateRequests(closeStatusMessage, closeStatus);
		}

		private String initCloseStatusMessage(CloseStatus status, @Nullable Throwable ex, GraphQlSession session) {
			String reason = session + " disconnected";
			if (isStopped()) {
				reason = session + " was stopped";
			}
			else if (ex != null) {
				reason += ", closeStatus() completed with error " + ex;
			}
			else if (!status.equals(CloseStatus.NO_STATUS_CODE)) {
				reason += " with " + status;
			}
			else {
				reason += " without a status";
			}
			return reason;
		}

		/**
		 * This must be called from code that calls the {@code WebSocketClient}
		 * when execution completes with an error, which includes connection and
		 * session handling issues, and handler is unaware of connection issues
		 * otherwise.
		 *
		 * <p>The exception is logged which may provide further information,
		 * beyond the CloseStatus, when closed locally due to handling completing
		 * with an error. The error is routed to subscribers of
		 * {@link #getGraphQlSession()} which is necessary for connection issues.
		 */
		void handleWebSocketSessionError(Throwable ex) {

			if (logger.isDebugEnabled()) {
				logger.debug("Session handling error: " + ex.getMessage(), ex);
			}
			else if (logger.isErrorEnabled()) {
				logger.error("Session handling error: " + ex.getMessage());
			}

			this.graphQlSessionSink.tryEmitError(ex);
			this.graphQlSessionSink = Sinks.unsafe().one();
		}

		/**
		 * This must be called from code that calls the {@code WebSocketClient}
		 * when execution completes.
		 */
		void handleWebSocketSessionClosed() {
			this.graphQlSessionSink = Sinks.unsafe().one();
		}

	}


	/**
	 * Session that deals with GraphQL level concerns such as sending requests,
	 * handling and routing responses, managing the lifecycle of streams, and
	 * to allow higher level code to be notified of closing or to close the
	 * underlying WebSocket.
	 */
	private static class GraphQlSession {

		private final DisposableConnection connection;

		private final AtomicLong requestIndex = new AtomicLong();

		private final RequestSink requestSink = new RequestSink();

		private final Map<String, RequestState> requestStateMap = new ConcurrentHashMap<>();

		private boolean hasReceivedMessages;


		GraphQlSession(WebSocketSession webSocketSession) {
			this.connection = DisposableConnection.from(webSocketSession);
		}


		/**
		 * Return the {@code Flux} of GraphQL requests to send as WebSocket messages.
		 */
		Flux<GraphQlWebSocketMessage> getRequestFlux() {
			return this.requestSink.getRequestFlux();
		}


		// Outbound messages

		Mono<GraphQlResponse> execute(GraphQlRequest request) {
			String id = String.valueOf(this.requestIndex.incrementAndGet());
			return Mono.<GraphQlResponse>create((sink) -> {
				SingleResponseRequestState state = new SingleResponseRequestState(request, sink);
				this.requestStateMap.put(id, state);
				try {
					GraphQlWebSocketMessage message = GraphQlWebSocketMessage.subscribe(id, request);
					this.requestSink.sendRequest(message);
				}
				catch (Exception ex) {
					this.requestStateMap.remove(id);
					sink.error(ex);
				}
			}).doOnCancel(() -> this.requestStateMap.remove(id));
		}

		Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
			String id = String.valueOf(this.requestIndex.incrementAndGet());
			return Flux.<GraphQlResponse>create((sink) -> {
				SubscriptionRequestState state = new SubscriptionRequestState(request, sink);
				this.requestStateMap.put(id, state);
				try {
					GraphQlWebSocketMessage message = GraphQlWebSocketMessage.subscribe(id, request);
					this.requestSink.sendRequest(message);
				}
				catch (Exception ex) {
					this.requestStateMap.remove(id);
					sink.error(ex);
				}
			}).doOnCancel(() -> stopSubscription(id));
		}

		private void stopSubscription(String id) {
			RequestState state = this.requestStateMap.remove(id);
			if (state != null) {
				try {
					this.requestSink.sendRequest(GraphQlWebSocketMessage.complete(id));
				}
				catch (Exception ex) {
					if (logger.isErrorEnabled()) {
						logger.error("Closing " + this.connection.getDescription() +
								" after failure to send 'complete' for subscription id='" + id + "'.");
					}
					// No other suitable status (like server error but there is none for client)
					this.connection.close(CloseStatus.PROTOCOL_ERROR).subscribe();
				}
			}
		}

		void sendPong(@Nullable Map<String, Object> payload) {
			GraphQlWebSocketMessage message = GraphQlWebSocketMessage.pong(payload);
			this.requestSink.sendRequest(message);
		}

		void sendPing() {
			GraphQlWebSocketMessage message = GraphQlWebSocketMessage.ping(null);
			this.requestSink.sendRequest(message);
		}

		boolean checkSentOrReceivedMessagesAndClear() {
			boolean received = this.hasReceivedMessages;
			this.hasReceivedMessages = false;
			return (this.requestSink.checkSentMessagesAndClear() || received);
		}

		// Inbound messages

		/**
		 * Handle a "next" message and route to its recipient.
		 */
		void handleNext(GraphQlWebSocketMessage message) {
			String id = message.getId();
			RequestState requestState = this.requestStateMap.get(id);
			if (requestState == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("No receiver for: " + message);
				}
				return;
			}

			this.hasReceivedMessages = true;

			if (requestState instanceof SingleResponseRequestState) {
				this.requestStateMap.remove(id);
			}

			Map<String, Object> payload = message.getPayload();
			GraphQlResponse graphQlResponse = new ResponseMapGraphQlResponse(payload);
			requestState.handleResponse(graphQlResponse);
		}

		/**
		 * Handle an "error" message, turning it into an {@link GraphQlResponse}
		 * for single responses, or signaling an error for streams.
		 */
		void handleError(GraphQlWebSocketMessage message) {
			String id = message.getId();
			RequestState requestState = this.requestStateMap.remove(id);
			if (requestState == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("No receiver for: " + message);
				}
				return;
			}

			List<Map<String, Object>> errorList = message.getPayload();
			GraphQlResponse response = new ResponseMapGraphQlResponse(Collections.singletonMap("errors", errorList));

			if (requestState instanceof SingleResponseRequestState) {
				requestState.handleResponse(response);
			}
			else {
				List<ResponseError> errors = response.getErrors();
				Exception ex = new SubscriptionErrorException(requestState.request(), errors);
				requestState.handlerError(ex);
			}
		}

		/**
		 * Handle a "complete" message.
		 */
		void handleComplete(GraphQlWebSocketMessage message) {
			String id = message.getId();
			RequestState requestState = this.requestStateMap.remove(id);
			if (requestState == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("No receiver for': " + message);
				}
				return;
			}
			requestState.handleCompletion();
		}

		/**
		 * Return a {@code Mono} that completes when the connection is closed
		 * for any reason.
		 */
		Mono<Void> notifyWhenClosed() {
			return this.connection.notifyWhenClosed();
		}

		/**
		 * Close the underlying connection.
		 */
		Mono<Void> close() {
			return this.connection.close(CloseStatus.GOING_AWAY);
		}

		/**
		 * Terminate and clean all in-progress requests with the given error.
		 */
		void terminateRequests(String message, CloseStatus status) {
			this.requestStateMap.values().forEach((info) -> info.emitDisconnectError(message, status));
			this.requestStateMap.clear();
		}

		@Override
		public String toString() {
			return "GraphQlSession over " + this.connection.getDescription();
		}

	}


	/**
	 * Minimal abstraction to decouple the {@link GraphQlSession} from the
	 * underlying {@code WebSocketSession}.
	 */
	private interface DisposableConnection {

		Mono<Void> close(CloseStatus status);

		Mono<Void> notifyWhenClosed();

		String getDescription();


		static DisposableConnection from(WebSocketSession session) {

			return new DisposableConnection() {

				@Override
				public Mono<Void> close(CloseStatus status) {
					return session.close(status);
				}

				@Override
				public Mono<Void> notifyWhenClosed() {
					return session.closeStatus().then();
				}

				@Override
				public String getDescription() {
					return session.toString();
				}

			};
		}
	}


	/**
	 * Holds the request {@code Flux} and associated {@link FluxSink}.
	 */
	private static final class RequestSink {

		private @Nullable FluxSink<GraphQlWebSocketMessage> requestSink;

		private boolean hasSentMessages;

		private final Flux<GraphQlWebSocketMessage> requestFlux = Flux.create((sink) -> {
			Assert.state(this.requestSink == null, "Expected single subscriber only for outbound messages");
			this.requestSink = sink;
		});

		Flux<GraphQlWebSocketMessage> getRequestFlux() {
			return this.requestFlux;
		}

		void sendRequest(GraphQlWebSocketMessage message) {
			Assert.state(this.requestSink != null, "Unexpected request before Flux is subscribed to");
			this.hasSentMessages = true;
			this.requestSink.next(message);
		}

		boolean checkSentMessagesAndClear() {
			boolean result = this.hasSentMessages;
			this.hasSentMessages = false;
			return result;
		}

	}


	/**
	 * Base class, state container for any request type.
	 */
	private interface RequestState {

		GraphQlRequest request();

		void handleResponse(GraphQlResponse response);

		void handlerError(Throwable ex);

		void handleCompletion();

		default void emitDisconnectError(String message, CloseStatus closeStatus) {
			emitDisconnectError(new WebSocketDisconnectedException(message, request(), closeStatus));
		}

		void emitDisconnectError(WebSocketDisconnectedException ex);

	}


	/**
	 * State container for a request that emits a single response.
	 */
	private record SingleResponseRequestState(
			GraphQlRequest request, MonoSink<GraphQlResponse> responseSink) implements RequestState {

		@Override
		public void handleResponse(GraphQlResponse response) {
			this.responseSink.success(response);
		}

		@Override
		public void handlerError(Throwable ex) {
			this.responseSink.error(ex);
		}

		@Override
		public void handleCompletion() {
			this.responseSink.success();
		}

		@Override
		public void emitDisconnectError(WebSocketDisconnectedException ex) {
			handlerError(ex);
		}

	}


	/**
	 * State container for a subscription request that emits a stream of responses.
	 */
	private record SubscriptionRequestState(
			GraphQlRequest request, FluxSink<GraphQlResponse> responseSink) implements RequestState {

		@Override
		public void handleResponse(GraphQlResponse response) {
			this.responseSink.next(response);
		}

		@Override
		public void handlerError(Throwable ex) {
			this.responseSink.error(ex);
		}

		@Override
		public void handleCompletion() {
			this.responseSink.complete();
		}

		@Override
		public void emitDisconnectError(WebSocketDisconnectedException ex) {
			handlerError(ex);
		}

	}

}

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

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import graphql.GraphQLError;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.support.MapGraphQlError;
import org.springframework.graphql.support.MapGraphQlResponse;
import org.springframework.graphql.web.support.GraphQlMessage;
import org.springframework.graphql.web.support.GraphQlMessageType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

/**
 * {@link GraphQlTransport} for GraphQL over WebSocket via {@link WebSocketClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL over WebSocket protocol</a>
 */
final class WebSocketGraphQlTransport implements GraphQlTransport {

	private static final Log logger = LogFactory.getLog(WebSocketGraphQlTransport.class);

	private final URI url;

	private final HttpHeaders headers = new HttpHeaders();

	private final WebSocketClient webSocketClient;

	private final GraphQlSessionHandler graphQlSessionHandler;

	private final Mono<GraphQlSession> graphQlSessionMono;


	WebSocketGraphQlTransport(
			URI url, @Nullable HttpHeaders headers, WebSocketClient client, CodecConfigurer codecConfigurer,
			@Nullable Object connectionInitPayload, Consumer<Map<String, Object>> connectionAckHandler) {

		Assert.notNull(url, "URI is required");
		Assert.notNull(url, "URI is required");

		this.url = url;
		this.headers.putAll(headers != null ? headers : HttpHeaders.EMPTY);
		this.webSocketClient = client;

		this.graphQlSessionHandler = new GraphQlSessionHandler(
				codecConfigurer, connectionInitPayload, connectionAckHandler);

		this.graphQlSessionMono = initGraphQlSession(this.url, this.headers, client, this.graphQlSessionHandler)
				.cacheInvalidateWhen(GraphQlSession::notifyWhenClosed);
	}

	private static Mono<GraphQlSession> initGraphQlSession(
			URI uri, HttpHeaders headers, WebSocketClient client, GraphQlSessionHandler handler) {

		return Mono.defer(() -> {
			if (handler.isStopped()) {
				return Mono.error(new IllegalStateException("WebSocketGraphQlTransport has been stopped"));
			}

			client.execute(uri, headers, handler)
					.subscribe(aVoid -> {}, handler::handleWebSocketSessionError, () -> {});

			return handler.getGraphQlSession();
		});
	}


	public URI getUrl() {
		return this.url;
	}

	public HttpHeaders getHeaders() {
		return this.headers;
	}

	public WebSocketClient getWebSocketClient() {
		return this.webSocketClient;
	}

	public CodecConfigurer getCodecConfigurer() {
		return this.graphQlSessionHandler.getCodecConfigurer();
	}


	/**
	 * Start the transport by connecting the WebSocket, sending the
	 * "connection_init" and waiting for the "connection_ack" message.
	 * @return {@code Mono} that completes when the WebSocket is connected and
	 * ready to begin sending GraphQL requests
	 */
	public Mono<Void> start() {
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
	public Mono<Void> stop() {
		this.graphQlSessionHandler.setStopped(true);
		return this.graphQlSessionMono.flatMap(GraphQlSession::close).onErrorResume(ex -> Mono.empty());
	}

	@Override
	public Mono<GraphQlResponse> execute(GraphQlRequest request) {
		return this.graphQlSessionMono.flatMap(session -> session.execute(request));
	}

	@Override
	public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
		return this.graphQlSessionMono.flatMapMany(session -> session.executeSubscription(request));
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

		private final GraphQlMessage connectionInitMessage;

		private final Consumer<Map<String, Object>> connectionAckHandler;

		private Sinks.One<GraphQlSession> graphQlSessionSink;

		private final AtomicBoolean stopped = new AtomicBoolean();


		GraphQlSessionHandler(CodecConfigurer codecConfigurer,
				@Nullable Object connectionInitPayload, Consumer<Map<String, Object>> connectionAckHandler) {

			this.codecDelegate = new CodecDelegate(codecConfigurer);
			this.connectionInitMessage = GraphQlMessage.connectionInit(connectionInitPayload);
			this.connectionAckHandler = connectionAckHandler;
			this.graphQlSessionSink = Sinks.unsafe().one();
		}


		public CodecConfigurer getCodecConfigurer() {
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
		public Mono<GraphQlSession> getGraphQlSession() {
			return this.graphQlSessionSink.asMono();
		}

		/**
		 * When the handler is marked "stopped", i.e. set to {@code true}, new
		 * requests are rejected. When set to {@code true} they are allowed.
		 */
		public void setStopped(boolean stopped) {
			this.stopped.set(stopped);
		}

		/**
		 * Whether the handler is marked {@link #setStopped(boolean) "stopped"}.
		 */
		public boolean isStopped() {
			return this.stopped.get();
		}


		@Override
		public Mono<Void> handle(WebSocketSession session) {

			Assert.state(sessionNotInitialized(),
					"This handler supports only one session at a time, for shared use.");

			GraphQlSession graphQlSession = new GraphQlSession(session);
			registerCloseStatusHandling(graphQlSession, session);

			Mono<Void> sendCompletion =
					session.send(Flux.just(this.connectionInitMessage).concatWith(graphQlSession.getRequestFlux())
							.map(message -> this.codecDelegate.encode(session, message)));

			Mono<Void> receiveCompletion = session.receive()
					.flatMap(webSocketMessage -> {
						if (sessionNotInitialized()) {
							try {
								GraphQlMessage message = this.codecDelegate.decode(webSocketMessage);
								Assert.state(message.resolvedType() == GraphQlMessageType.CONNECTION_ACK,
										() -> "Unexpected message before connection_ack: " + message);
								this.connectionAckHandler.accept(message.getPayload());
								if (logger.isDebugEnabled()) {
									logger.debug(graphQlSession + " initialized");
								}
							}
							catch (Throwable ex) {
								this.graphQlSessionSink.tryEmitError(ex);
								return Mono.error(ex);
							}
							Sinks.EmitResult emitResult = this.graphQlSessionSink.tryEmitValue(graphQlSession);
							if (emitResult.isFailure()) {
								return Mono.error(new IllegalStateException(
										"GraphQlSession initialized but could not be emitted: " + emitResult));
							}
						}
						else {
							GraphQlMessage message = this.codecDelegate.decode(webSocketMessage);
							switch (message.resolvedType()) {
								case NEXT:
									graphQlSession.handleNext(message);
									break;
								case PING:
									graphQlSession.sendPong(null);
									break;
								case ERROR:
									graphQlSession.handleError(message);
									break;
								case COMPLETE:
									graphQlSession.handleComplete(message);
									break;
								default:
									return session.close(new CloseStatus(4400, "Invalid message"));
							}
						}
						return Mono.empty();
					})
					.then();

			return Mono.zip(sendCompletion, receiveCompletion).then();
		}

		private boolean sessionNotInitialized() {
			return !Boolean.TRUE.equals(this.graphQlSessionSink.scan(Scannable.Attr.TERMINATED));
		}

		private void registerCloseStatusHandling(GraphQlSession graphQlSession, WebSocketSession session) {
			session.closeStatus()
					.defaultIfEmpty(CloseStatus.NO_STATUS_CODE)
					.doOnNext(closeStatus -> {
						Exception ex = initDisconnectError(closeStatus, null, graphQlSession);
						if (logger.isDebugEnabled()) {
							logger.debug(ex.getMessage());
						}
						graphQlSession.terminateRequests(ex);
					})
					.doOnError(cause -> {
						Exception ex = initDisconnectError(null, cause, graphQlSession);
						if (logger.isErrorEnabled()) {
							logger.error(ex.getMessage());
						}
						graphQlSession.terminateRequests(ex);
					})
					.doOnTerminate(() -> {
						// Reset GraphQlSession sink to be ready to connect again
						this.graphQlSessionSink = Sinks.unsafe().one();
					})
					.subscribe();
		}

		private Exception initDisconnectError(
				@Nullable CloseStatus status, @Nullable Throwable ex, GraphQlSession graphQlSession) {

			String reason = graphQlSession + " disconnected";
			if (isStopped()) {
				reason = graphQlSession + " was stopped";
			}
			else if (ex != null) {
				reason += ", closeStatus() completed with error " + ex;
			}
			else if (status != null && !status.equals(CloseStatus.NO_STATUS_CODE)) {
				reason += " with " + status;
			}
			else {
				reason += " without a status";
			}
			return new IllegalStateException(reason);
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
		public void handleWebSocketSessionError(Throwable ex) {

			if (logger.isDebugEnabled()) {
				logger.debug("Session handling error: " + ex.getMessage(), ex);
			}
			else if (logger.isErrorEnabled()) {
				logger.error("Session handling error: " + ex.getMessage());
			}

			this.graphQlSessionSink.tryEmitError(ex);
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

		private final Sinks.Many<GraphQlMessage> requestSink = Sinks.many().unicast().onBackpressureBuffer();

		private final Map<String, Sinks.One<GraphQlResponse>> responseSinks = new ConcurrentHashMap<>();

		private final Map<String, Sinks.Many<GraphQlResponse>> streamSinks = new ConcurrentHashMap<>();


		GraphQlSession(WebSocketSession webSocketSession) {
			this.connection = DisposableConnection.from(webSocketSession);
		}


		/**
		 * Return the {@code Flux} of GraphQL requests to send as WebSocket messages.
		 */
		public Flux<GraphQlMessage> getRequestFlux() {
			return this.requestSink.asFlux();
		}

		public Mono<GraphQlResponse> execute(GraphQlRequest request) {
			String id = String.valueOf(this.requestIndex.incrementAndGet());
			try {
				GraphQlMessage message = GraphQlMessage.subscribe(id, request);
				Sinks.One<GraphQlResponse> sink = Sinks.one();
				this.responseSinks.put(id, sink);
				trySend(message);
				return sink.asMono().doOnCancel(() -> this.responseSinks.remove(id));
			}
			catch (Exception ex) {
				this.responseSinks.remove(id);
				return Mono.error(ex);
			}
		}

		public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
			String id = String.valueOf(this.requestIndex.incrementAndGet());
			try {
				GraphQlMessage message = GraphQlMessage.subscribe(id, request);
				Sinks.Many<GraphQlResponse> sink = Sinks.many().unicast().onBackpressureBuffer();
				this.streamSinks.put(id, sink);
				trySend(message);
				return sink.asFlux().doOnCancel(() -> cancelStream(id));
			}
			catch (Exception ex) {
				this.streamSinks.remove(id);
				return Flux.error(ex);
			}
		}

		public void sendPong(@Nullable Map<String, Object> payload) {
			GraphQlMessage message = GraphQlMessage.pong(payload);
			trySend(message);
		}

		// TODO: queue to serialize sending?

		private void trySend(GraphQlMessage message) {
			Sinks.EmitResult emitResult = null;
			for (int i = 0; i < 100; i++) {
				emitResult = this.requestSink.tryEmitNext(message);
				if (emitResult != Sinks.EmitResult.FAIL_NON_SERIALIZED) {
					break;
				}
			}
			Assert.state(emitResult.isSuccess(), "Failed to send request: " + emitResult);
		}

		private void cancelStream(String id) {
			Sinks.Many<GraphQlResponse> streamSink = this.streamSinks.remove(id);
			if (streamSink != null) {
				try {
					trySend(GraphQlMessage.complete(id));
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

		/**
		 * Handle a "next" message and route to its recipient.
		 */
		public void handleNext(GraphQlMessage message) {
			String id = message.getId();
			Sinks.One<GraphQlResponse> sink = this.responseSinks.remove(id);
			Sinks.Many<GraphQlResponse> streamingSink = this.streamSinks.get(id);

			if (sink == null && streamingSink == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("No receiver for message: " + message);
				}
				return;
			}

			Map<String, Object> responseMap = message.getPayload();
			GraphQlResponse response = MapGraphQlResponse.forResponse(responseMap);

			Sinks.EmitResult emitResult = (sink != null ? sink.tryEmitValue(response) : streamingSink.tryEmitNext(response));
			if (emitResult.isFailure()) {
				// Just log: cannot overflow, is serialized, and cancel is handled in doOnCancel
				if (logger.isDebugEnabled()) {
					logger.debug("Message: " + message + " could not be emitted: " + emitResult);
				}
			}
		}

		/**
		 * Handle an "error" message, turning it into an {@link GraphQlResponse}
		 * for single responses, or signaling an error for streams.
		 */
		public void handleError(GraphQlMessage message) {
			String id = message.getId();
			Sinks.One<GraphQlResponse> sink = this.responseSinks.remove(id);
			Sinks.Many<GraphQlResponse> streamingSink = this.streamSinks.remove(id);

			if (sink == null && streamingSink == null ) {
				if (logger.isDebugEnabled()) {
					logger.debug("No receiver for message: " + message);
				}
				return;
			}

			List<Map<String, Object>> errorList = message.getPayload();

			Sinks.EmitResult emitResult;
			if (sink != null) {
				GraphQlResponse response = MapGraphQlResponse.forErrorsOnly(errorList);
				emitResult = sink.tryEmitValue(response);
			}
			else {
				List<GraphQLError> graphQLErrors = MapGraphQlError.from(errorList);
				Exception ex = new SubscriptionErrorException(graphQLErrors);
				emitResult = streamingSink.tryEmitError(ex);
			}

			if (emitResult.isFailure() && logger.isDebugEnabled()) {
				logger.debug("Error: " + message + " could not be emitted: " + emitResult);
			}
		}

		/**
		 * Handle a "complete" message.
		 */
		public void handleComplete(GraphQlMessage message) {
			Sinks.One<GraphQlResponse> sink = this.responseSinks.remove(message.getId());
			Sinks.Many<GraphQlResponse> streamSink = this.streamSinks.remove(message.getId());

			if (sink != null) {
				sink.tryEmitEmpty();
			}
			else if (streamSink != null) {
				streamSink.tryEmitComplete();
			}
		}

		/**
		 * Return a {@code Mono} that completes when the connection is closed
		 * for any reason.
		 */
		public Mono<Void> notifyWhenClosed() {
			return this.connection.notifyWhenClosed();
		}

		/**
		 * Close the underlying connection.
		 */
		public Mono<Void> close() {
			return this.connection.close(CloseStatus.GOING_AWAY);
		}

		/**
		 * Terminate and clean all in-progress requests with the given error.
		 */
		public void terminateRequests(Exception ex) {
			this.responseSinks.values().forEach(sink -> sink.tryEmitError(ex));
			this.streamSinks.values().forEach(sink -> sink.tryEmitError(ex));
			this.responseSinks.clear();
			this.streamSinks.clear();
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


}

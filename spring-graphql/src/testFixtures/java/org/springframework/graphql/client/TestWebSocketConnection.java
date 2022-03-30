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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.adapter.AbstractWebSocketSession;

/**
 * Emulates a WebSocket connection by connecting a client and a server handlers
 * via two message {@link Sinks.Many sinks}, one for each end of the connection.
 *
 * <p>Use {@link TestWebSocketClient} to establish connections that can then be
 * accessed through it.
 *
 * @author Rossen Stoyanchev
 */
public final class TestWebSocketConnection {

	private static Log logger = LogFactory.getLog(TestWebSocketConnection.class);

	private static final AtomicLong connectionIndex = new AtomicLong();


	private final URI url;

	private final HttpHeaders headers;

	private final TestWebSocketSession clientSession;

	private final TestWebSocketSession serverSession;


	public TestWebSocketConnection(URI url, HttpHeaders headers) {

		this.url = url;
		this.headers = headers;

		long id = connectionIndex.incrementAndGet();

		Sinks.Many<WebSocketMessage> clientSink = Sinks.many().unicast().onBackpressureBuffer();
		Sinks.Many<WebSocketMessage> serverSink = Sinks.many().unicast().onBackpressureBuffer();

		Sinks.One<CloseStatus> clientStatusSink = Sinks.one();
		Sinks.One<CloseStatus> serverStatusSink = Sinks.one();

		this.clientSession = new TestWebSocketSession("client-session-" + id, url, headers,
				clientSink, serverSink.asFlux(), clientStatusSink, serverStatusSink.asMono());

		this.serverSession = new TestWebSocketSession("server-session-" + id, url, headers,
				serverSink, clientSink.asFlux(), serverStatusSink, clientStatusSink.asMono());
	}


	public URI getUrl() {
		return this.url;
	}

	public HttpHeaders getHeaders() {
		return this.headers;
	}

	/**
	 * Return {@code true} if both client and server sessions are open.
	 */
	public boolean isOpen() {
		return (this.clientSession.isOpen() && this.serverSession.isOpen());
	}

	/**
	 * Return messages sent from the client side.
	 */
	public List<WebSocketMessage> getClientMessages() {
		return this.clientSession.getSentMessages();
	}

	/**
	 * Return messages sent from the server side.
	 */
	public List<WebSocketMessage> getServerMessages() {
		return this.serverSession.getSentMessages();
	}


	/**
	 * Starts client and server session handling, and if either side errors or
	 * completes, close its session with either {@link CloseStatus#NORMAL} or
	 * {@link CloseStatus#PROTOCOL_ERROR} respectively.
	 * @param clientHandler the client session handler
	 * @param serverHandler the server session handler
	 * @return {@code Mono} that completes when either client or server
	 * session handling completes or when either is closed
	 */
	Mono<Void> connect(WebSocketHandler clientHandler, WebSocketHandler serverHandler) {

		Mono<Void> serverMono = invokeHandler(serverHandler, this.serverSession, false);
		Mono<Void> clientMono = invokeHandler(clientHandler, this.clientSession, true);

		Mono<Void> serverStatusMono = this.serverSession.closeStatus().then();
		Mono<Void> clientStatusMono = this.clientSession.closeStatus().then();

		return Mono.zip(serverMono, clientMono, serverStatusMono, clientStatusMono).then();
	}

	/**
	 * Handle the session and complete it when handling completes.
	 */
	private Mono<Void> invokeHandler(WebSocketHandler handler, TestWebSocketSession session, boolean isClient) {
		return handler.handle(session)
				.then(Mono.defer(() -> session.close(CloseStatus.NORMAL)))
				.onErrorResume(ex -> {
					logger.error("Unhandled " + (isClient ? "client" : "server") + " error: " + ex.getMessage());
					return session.close(CloseStatus.PROTOCOL_ERROR).then(Mono.error(ex));
				});
	}


	/**
	 * Close the connection from the client side.
	 */
	public Mono<Void> closeClientSession(CloseStatus status) {
		return this.clientSession.close(status);
	}

	/**
	 * Close the connection from the server side.
	 */
	public Mono<Void> closeServerSession(CloseStatus status) {
		return this.serverSession.close(status);
	}

	/**
	 * Return the {@code CloseStatus} that may have come from either side.
	 */
	public Mono<CloseStatus> closeStatus() {
		return this.clientSession.closeStatus().or(this.serverSession.closeStatus());
	}


	/**
	 * Test WebSocketSession that sends to a given {@link Sinks.Many sink} and
	 * receives from a given {@code Flux}.
	 */
	private static class TestWebSocketSession extends AbstractWebSocketSession<Object> {

		private final Sinks.Many<WebSocketMessage> sendSink;

		private final Flux<WebSocketMessage> receiveFlux;

		private final Sinks.One<CloseStatus> closeStatusSink;

		private final Queue<WebSocketMessage> sentMessages = new ConcurrentLinkedQueue<>();


		TestWebSocketSession(String sessionId, URI url, HttpHeaders headers,
				Sinks.Many<WebSocketMessage> sendSink, Flux<WebSocketMessage> receiveFlux,
				Sinks.One<CloseStatus> closeStatusSink, Mono<CloseStatus> remoteCloseStatusMono) {

			super(new Object(), sessionId, new HandshakeInfo(url, headers, Mono.empty(), null),
					DefaultDataBufferFactory.sharedInstance);

			this.sendSink = sendSink;
			this.receiveFlux = receiveFlux.cache();
			this.closeStatusSink = closeStatusSink;

			// Close this side when the remote closes
			remoteCloseStatusMono.doOnSuccess(this::handleRemoteClosure).subscribe();
		}

		private void handleRemoteClosure(@Nullable CloseStatus status) {

			if (!isOpen()) {
				// when we close, remote closes, and we detect that
				return;
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this + " due to remote " + status);
			}

			closeInternal(status);
		}


		public List<WebSocketMessage> getSentMessages() {
			return new ArrayList<>(this.sentMessages);
		}

		@Override
		public Mono<Void> send(Publisher<WebSocketMessage> messages) {
			return Flux.from(messages)
					.doOnNext(this::saveMessage)
					.doOnNext(message -> {
						Sinks.EmitResult result = this.sendSink.tryEmitNext(message);
						Assert.state(result.isSuccess(), this + " failed to send: " + message + ", with " + result);
					})
					.then();
		}

		private void saveMessage(WebSocketMessage message) {
			DataBuffer payload = message.getPayload().retainedSlice(0, message.getPayload().readableByteCount());
			this.sentMessages.add(new WebSocketMessage(message.getType(), payload));
		}

		@Override
		public Flux<WebSocketMessage> receive() {
			return this.receiveFlux;
		}

		@Override
		public boolean isOpen() {
			return !Boolean.TRUE.equals(this.closeStatusSink.scan(Scannable.Attr.TERMINATED));
		}

		@Override
		public Mono<CloseStatus> closeStatus() {
			return this.closeStatusSink.asMono();
		}

		public Mono<Void> close(CloseStatus status) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this + " with " + status);
			}
			closeInternal(status);
			return Mono.empty();
		}

		private void closeInternal(@Nullable CloseStatus status) {
			if (status != null) {
				this.closeStatusSink.tryEmitValue(status);
			}
			else {
				this.closeStatusSink.tryEmitEmpty();
			};
		}

		@Override
		public String toString() {
			return getId();
		}

	}

}

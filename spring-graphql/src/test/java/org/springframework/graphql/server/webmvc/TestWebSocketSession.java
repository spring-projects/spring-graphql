/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.webmvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocketSession that saves sent messages and exposes them as a Flux which makes
 * assertions comparable to the same for WebFlux.
 */
public class TestWebSocketSession implements WebSocketSession {

	private final URI uri = URI.create("https://example.org/graphql");

	private final HttpHeaders headers = new HttpHeaders();

	private final Map<String, Object> attributes = new ConcurrentHashMap<>();

	private final Sinks.Many<WebSocketMessage<?>> messagesSink = Sinks.many().unicast().onBackpressureBuffer();

	private Sinks.One<CloseStatus> statusSink = Sinks.one();

	private boolean closed;

	@Override
	public String getId() {
		return "1";
	}

	@Override
	public URI getUri() {
		return this.uri;
	}

	@Override
	public HttpHeaders getHandshakeHeaders() {
		return this.headers;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

	@Override
	public Principal getPrincipal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		throw new UnsupportedOperationException();
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return null;
	}

	@Override
	public String getAcceptedProtocol() {
		return "graphql-transport-ws";
	}

	@Override
	public void setTextMessageSizeLimit(int messageSizeLimit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getTextMessageSizeLimit() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBinaryMessageSizeLimit(int messageSizeLimit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getBinaryMessageSizeLimit() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<WebSocketExtension> getExtensions() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendMessage(WebSocketMessage<?> message) throws IOException {
		synchronized (this.messagesSink) {
			emitMessagesSignal(this.messagesSink.tryEmitNext(message));
		}
	}

	private void emitMessagesSignal(Sinks.EmitResult result) {
		Assert.state(result == Sinks.EmitResult.OK, "Emit failed: " + result);
	}

	public Flux<WebSocketMessage<?>> getOutput() {
		return this.messagesSink.asFlux();
	}

	@Override
	public boolean isOpen() {
		return !this.closed;
	}

	@Override
	public void close() {
		this.closed = true;
		synchronized (this.messagesSink) {
			emitMessagesSignal(this.messagesSink.tryEmitComplete());
		}
		this.statusSink.tryEmitEmpty();
	}

	@Override
	public void close(CloseStatus status) {
		this.closed = true;
		synchronized (this.messagesSink) {
			emitMessagesSignal(this.messagesSink.tryEmitComplete());
		}
		this.statusSink.tryEmitValue(status);
	}

	public @Nullable CloseStatus getCloseStatus() {
		return (this.closed ? this.statusSink.asMono().block() : null);
	}

	public Mono<CloseStatus> closeStatus() {
		return this.statusSink.asMono();
	}

}

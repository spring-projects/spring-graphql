/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.webflux;

import java.net.URI;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.adapter.AbstractWebSocketSession;

/**
 * {@link org.springframework.web.reactive.socket.WebSocketSession} that is given an input
 * Flux of messages and exposes a Flux of published output messages.
 */
class TestWebSocketSession extends AbstractWebSocketSession<Object> {

	private final Flux<WebSocketMessage> input;

	private Flux<WebSocketMessage> output = Flux.empty();

	private final Sinks.One<CloseStatus> closeStatusSink = Sinks.one();

	TestWebSocketSession(Flux<WebSocketMessage> input) {
		this("1", URI.create("https://example.org/graphql"), input);
	}

	TestWebSocketSession(String id, URI uri, Flux<WebSocketMessage> input) {
		super(new Object(), id, new HandshakeInfo(uri, new HttpHeaders(), Mono.empty(), null),
				DefaultDataBufferFactory.sharedInstance);
		this.input = input;
	}

	@Override
	public Flux<WebSocketMessage> receive() {
		return this.input;
	}

	@Override
	public Mono<Void> send(Publisher<WebSocketMessage> messages) {
		this.output = Flux.from(messages);
		return Mono.empty();
	}

	Flux<WebSocketMessage> getOutput() {
		return this.output;
	}

	@Override
	public boolean isOpen() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<Void> close(CloseStatus status) {
		this.closeStatusSink.tryEmitValue(status);
		return Mono.empty();
	}

	@Override
	public Mono<CloseStatus> closeStatus() {
		return this.closeStatusSink.asMono();
	}

}

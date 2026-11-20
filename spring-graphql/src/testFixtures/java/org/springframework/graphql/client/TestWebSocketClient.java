/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.client.WebSocketClient;

/**
 * {@link WebSocketClient} that uses {@link TestWebSocketConnection} to connect
 * and test the interaction between a client and a server {@link WebSocketHandler}.
 *
 * <p>Call {@link #execute(URI, WebSocketHandler)}, subscribe, and then use
 * getters to access established connections by index from based on order of
 * execution.
 *
 * @author Rossen Stoyanchev
 */
public final class TestWebSocketClient implements WebSocketClient {

	private final WebSocketHandler serverHandler;

	private final List<TestWebSocketConnection> connections = new CopyOnWriteArrayList<>();


	public TestWebSocketClient(WebSocketHandler serverHandler) {
		this.serverHandler = serverHandler;
	}


	/**
	 * Return the connection at the specified index from a list of connections
	 * based on order of execution.
	 * @param index the index of the connection to return
	 */
	public TestWebSocketConnection getConnection(int index) {
		Assert.isTrue(index < this.connections.size(),
				"No connection at index=" + index + ", total=" + this.connections.size());
		return this.connections.get(index);
	}

	/**
	 * Return the number of connections which corresponds to the number of calls
	 * to one of the execute methods.
	 */
	public int getConnectionCount() {
		return this.connections.size();
	}


	@Override
	public Mono<Void> execute(URI url, WebSocketHandler clientHandler) {
		return execute(URI.create("/"), HttpHeaders.EMPTY, clientHandler);
	}

	@Override
	public Mono<Void> execute(URI url, HttpHeaders headers, WebSocketHandler clientHandler) {
		TestWebSocketConnection connection = new TestWebSocketConnection(url, headers);
		this.connections.add(connection);
		return connection.connect(clientHandler, this.serverHandler);
	}

}

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
	 */
	public TestWebSocketConnection getConnection(int index) {
		Assert.isTrue(index < this.connections.size(),
				"No connection at index=" + index + ", total=" + this.connections.size());
		return connections.get(index);
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

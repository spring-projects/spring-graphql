/*
 * Copyright 2002-2020 the original author or authors.
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
package org.springframework.graphql;

import java.util.Map;

import org.springframework.web.reactive.socket.HandshakeInfo;

/**
 * Extension of {@link WebInput} that contains a GraphQL subscription received
 * over a WebSocket connection.
 */
public class WebSocketInput extends WebInput {

	private final HandshakeInfo handshakeInfo;

	private final String id;


	public WebSocketInput(HandshakeInfo handshakeInfo, String id, Map<String, Object> payload) {
		super(handshakeInfo.getUri(), handshakeInfo.getHeaders(), payload);
		this.handshakeInfo = handshakeInfo;
		this.id = id;
	}


	/**
	 * Return information about the WebSocket handshake.
	 */
	public HandshakeInfo handshakeInfo() {
		return this.handshakeInfo;
	}

	/**
	 * Return the id that will correlate server responses to client requests
	 * within a multiplexed WebSocket connection.
	 */
	public String id() {
		return this.id;
	}

	@Override
	public String toString() {
		return "id='" + id() + "', " + super.toString();
	}

}

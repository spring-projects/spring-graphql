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

package org.springframework.graphql.server.support;


/**
 * Enum for a message type as defined in the GraphQL over WebSocket spec proposal.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a>
 */
public enum GraphQlWebSocketMessageType {

	/**
	 * Indicates that the client wants to establish a connection within the existing socket.
	 */
	CONNECTION_INIT("connection_init", false),

	/**
	 * Expected response to the {@link #CONNECTION_INIT} message from the client acknowledging a successful connection with the server.
	 */
	CONNECTION_ACK("connection_ack", false),

	/**
	 * Useful for detecting failed connections, displaying latency metrics or other types of network probing.
	 */
	PING("ping", false),

	/**
	 * The response to the {@link #PING} message. Must be sent as soon as the {@link #PING} message is received.
	 */
	PONG("pong", false),

	/**
	 * Requests an operation specified in the message payload.
	 */
	SUBSCRIBE("subscribe", true),

	/**
	 * Operation execution result(s) from the source stream created by the binding {@link #SUBSCRIBE} message.
	 */
	NEXT("next", true),

	/**
	 * Operation execution error(s) in response to the {@link #SUBSCRIBE} message.
	 */
	ERROR("error", true),

	/**
	 * Indicates that the requested operation execution has completed.
	 */
	COMPLETE("complete", false),

	/**
	 * Indicates the GraphQL message did not have a message type.
	 */
	NOT_SPECIFIED("", false);


	private static final GraphQlWebSocketMessageType[] VALUES;

	static {
		VALUES = values();
	}


	private final String value;

	private final boolean requiresPayload;


	GraphQlWebSocketMessageType(String value, boolean requiresPayload) {
		this.value = value;
		this.requiresPayload = requiresPayload;
	}


	/**
	 * The protocol value for the message type.
	 */
	public String value() {
		return this.value;
	}

	/**
	 * Return {@code } if the message type has a payload, and it is required.
	 */
	public boolean doesNotRequirePayload() {
		return !this.requiresPayload;
	}


	public static GraphQlWebSocketMessageType fromValue(String value) {
		for (GraphQlWebSocketMessageType type : VALUES) {
			if (type.value.equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("No matching constant for [%s]".formatted(value));
	}


	@Override
	public String toString() {
		return this.value;
	}

}

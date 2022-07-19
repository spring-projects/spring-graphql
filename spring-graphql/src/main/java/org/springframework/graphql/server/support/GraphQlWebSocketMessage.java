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

package org.springframework.graphql.server.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.GraphQLError;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;


/**
 * Represents a GraphQL over WebSocket protocol message.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a>
 */
public class GraphQlWebSocketMessage {

	@Nullable
	private String id;

	@Nullable
	private GraphQlWebSocketMessageType type;

	@Nullable
	private Object payload;


	/**
	 * Private constructor. See static factory methods.
	 */
	private GraphQlWebSocketMessage(@Nullable String id, GraphQlWebSocketMessageType type, @Nullable Object payload) {
		Assert.notNull(type, "GraphQlMessageType is required");
		Assert.isTrue(payload != null || type.doesNotRequirePayload(), "Payload is required for [" + type + "]");
		this.id = id;
		this.type = type;
		this.payload = payload;
	}


	/**
	 * Constructor for deserialization.
	 */
	@SuppressWarnings("unused")
	GraphQlWebSocketMessage() {
		this.type = GraphQlWebSocketMessageType.NOT_SPECIFIED;
	}


	/**
	 * Return the request id that is applicable to messages associated with a
	 * request, or {@code null} for connection level messages.
	 */
	@Nullable
	public String getId() {
		return this.id;
	}

	/**
	 * Return the message type value as it should appear on the wire.
	 */
	public String getType() {
		Assert.notNull(this.type, "Type is required");
		return this.type.value();
	}

	/**
	 * Return the message type as an emum.
	 */
	public GraphQlWebSocketMessageType resolvedType() {
		Assert.state(this.type != null, "GraphQlWebSocketMessage does not have a type");
		return this.type;
	}

	/**
	 * Return the payload. For a deserialized message, this is typically a
	 * {@code Map} or {@code List} for an {@code "error"} message.
	 */
	@SuppressWarnings("unchecked")
	public <P> P getPayload() {
		if (this.payload == null) {
			Assert.state(resolvedType().doesNotRequirePayload(), this.type + " requires a payload");
			return (P) Collections.emptyMap();
		}
		return (P) this.payload;
	}

	public void setId(@Nullable String id) {
		this.id = id;
	}

	public void setType(String type) {
		this.type = GraphQlWebSocketMessageType.fromValue(type);
	}

	public void setPayload(@Nullable Object payload) {
		this.payload = payload;
	}


	@Override
	public int hashCode() {
		int hashCode = (this.type != null ? this.type.hashCode() : 0);
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.id);
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.payload);
		return hashCode;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GraphQlWebSocketMessage)) {
			return false;
		}
		GraphQlWebSocketMessage other = (GraphQlWebSocketMessage) o;
		return (ObjectUtils.nullSafeEquals(this.type, other.type) &&
				(ObjectUtils.nullSafeEquals(this.id, other.id) || (this.id == null && other.id == null)) &&
				(ObjectUtils.nullSafeEquals(getPayload(), other.getPayload())));
	}

	@Override
	public String toString() {
		return "GraphQlWebSocketMessage[" +
				(this.id != null ? "id=\"" + this.id + "\"" + ", " : "") +
				"type=\"" + this.type + "\"" +
				(this.payload != null ? ", payload=" + this.payload : "") + "]";
	}


	/**
	 * Create a {@code "connection_init"} client message.
	 * @param payload an optional payload
	 */
	public static GraphQlWebSocketMessage connectionInit(@Nullable Object payload) {
		return new GraphQlWebSocketMessage(null, GraphQlWebSocketMessageType.CONNECTION_INIT, payload);
	}

	/**
	 * Create a {@code "connection_ack"} server message.
	 * @param payload an optional payload
	 */
	public static GraphQlWebSocketMessage connectionAck(@Nullable Object payload) {
		return new GraphQlWebSocketMessage(null, GraphQlWebSocketMessageType.CONNECTION_ACK, payload);
	}

	/**
	 * Create a {@code "subscribe"} client message.
	 * @param id unique request id
	 * @param request the request to add as the message payload
	 */
	public static GraphQlWebSocketMessage subscribe(String id, GraphQlRequest request) {
		Assert.notNull(request, "GraphQlRequest is required");
		return new GraphQlWebSocketMessage(id, GraphQlWebSocketMessageType.SUBSCRIBE, request.toMap());
	}

	/**
	 * Create a {@code "next"} server message.
	 * @param id unique request id
	 * @param responseMap the response map
	 */
	public static GraphQlWebSocketMessage next(String id, Map<String, Object> responseMap) {
		Assert.notNull(responseMap, "'responseMap' is required");
		return new GraphQlWebSocketMessage(id, GraphQlWebSocketMessageType.NEXT, responseMap);
	}

	/**
	 * Create an {@code "error"} server message.
	 * @param id unique request id
	 * @param errors the error to add as the message payload
	 */
	public static GraphQlWebSocketMessage error(String id, List<GraphQLError> errors) {
		Assert.notNull(errors, "GraphQlError's are required");
		return new GraphQlWebSocketMessage(id, GraphQlWebSocketMessageType.ERROR,
				errors.stream().map(GraphQLError::toSpecification).collect(Collectors.toList()));
	}

	/**
	 * Create a {@code "complete"} server message.
	 * @param id unique request id
	 */
	public static GraphQlWebSocketMessage complete(String id) {
		return new GraphQlWebSocketMessage(id, GraphQlWebSocketMessageType.COMPLETE, null);
	}

	/**
	 * Create a {@code "ping"} client or server message.
	 * @param payload an optional payload
	 */
	public static GraphQlWebSocketMessage ping(@Nullable Object payload) {
		return new GraphQlWebSocketMessage(null, GraphQlWebSocketMessageType.PING, payload);
	}

	/**
	 * Create a {@code "pong"} client or server message.
	 * @param payload an optional payload
	 */
	public static GraphQlWebSocketMessage pong(@Nullable Object payload) {
		return new GraphQlWebSocketMessage(null, GraphQlWebSocketMessageType.PONG, payload);
	}

}

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

package org.springframework.graphql.web.webflux;

import java.util.Collections;

import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * Representation of a GraphQL over WebSocket protocol message.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlWebSocketMessage {

	@Nullable
	private String id;

	private String type;

	@Nullable
	private Object payload;


	/**
	 * Private constructor for static factory methods.
	 */
	private GraphQlWebSocketMessage(@Nullable String id, String type, @Nullable Object payload) {
		this.id = id;
		this.type = type;
		this.payload = payload;
	}

	/**
	 * Constructor for deserialization.
	 */
	GraphQlWebSocketMessage() {
		this.type = "";
	}


	@Nullable
	public String getId() {
		return this.id;
	}

	public String getType() {
		return this.type;
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public <P> P getPayload() {
		return (P) this.payload;
	}

	@SuppressWarnings("unchecked")
	public <P> P getPayloadOrDefault(P defaultPayload) {
		return (this.payload != null ? (P) this.payload : defaultPayload);
	}

	public void setId(@Nullable String id) {
		this.id = id;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setPayload(@Nullable Object payload) {
		this.payload = payload;
	}


	@Override
	public int hashCode() {
		int hashCode = this.type.hashCode();
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
		return (this.type.equals(other.type) &&
				(ObjectUtils.nullSafeEquals(this.id, other.id) || (this.id == null && other.id == null)) &&
				(ObjectUtils.nullSafeEquals(this.payload, other.payload) || (this.payload == null && other.payload == null)));
	}

	@Override
	public String toString() {
		return "GraphQlWebSocketMessage[" +
				(this.id != null ? "id=\"" + this.id + "\"" + ", " : "") +
				"type=\"" + this.type + "\"" +
				(this.payload != null ? ", payload=" + this.payload : "") + "]";
	}


	/**
	 * Create a "connection_init" message.
	 */
	public static GraphQlWebSocketMessage connectionInit(@Nullable Object payload) {
		return new GraphQlWebSocketMessage(null, "connection_init", payload);
	}

	/**
	 * Create a "connection_ack" message.
	 */
	public static GraphQlWebSocketMessage connectionAck(@Nullable Object payload) {
		return new GraphQlWebSocketMessage(null, "connection_ack", payload);
	}

	/**
	 * Create a "subscribe" message.
	 */
	public static GraphQlWebSocketMessage subscribe(String id, GraphQlRequest request) {
		return new GraphQlWebSocketMessage(id, "subscribe", request.toMap());
	}

	/**
	 * Create a "next" message.
	 */
	public static GraphQlWebSocketMessage next(String id, ExecutionResult result) {
		return new GraphQlWebSocketMessage(id, "next", result.toSpecification());
	}

	/**
	 * Create an "error" message.
	 */
	public static GraphQlWebSocketMessage error(String id, GraphQLError error) {
		return new GraphQlWebSocketMessage(id, "error", Collections.singletonList(error.toSpecification()));
	}

	/**
	 * Create a "complete" message.
	 */
	public static GraphQlWebSocketMessage complete(String id) {
		return new GraphQlWebSocketMessage(id, "complete", null);
	}

}

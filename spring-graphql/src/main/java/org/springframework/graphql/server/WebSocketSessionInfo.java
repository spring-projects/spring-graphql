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
package org.springframework.graphql.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;

/**
 * Expose information about the underlying WebSocketSession including the
 * session id, the attributes, and HTTP handshake request.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface WebSocketSessionInfo {

	/**
	 * Return the id for the WebSocketSession.
	 */
	String getId();

	/**
	 * Return the map with attributes associated with the WebSocket session.
	 */
	Map<String, Object> getAttributes();

	/**
	 * Return the URL for the WebSocket endpoint.
	 */
	URI getUri();

	/**
	 * Return the HTTP headers from the handshake request.
	 */
	HttpHeaders getHeaders();

	/**
	 * Return the principal associated with the handshake request, if any.
	 */
	Mono<Principal> getPrincipal();

	/**
	 * For a server session this is the remote address where the handshake
	 * request came from. For a client session, it is {@code null}.
	 */
	@Nullable
	InetSocketAddress getRemoteAddress();

}

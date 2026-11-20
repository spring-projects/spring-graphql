/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;

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
	@Nullable InetSocketAddress getRemoteAddress();

}

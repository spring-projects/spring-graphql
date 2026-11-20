/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;


import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;


/**
 * {@link org.springframework.graphql.server.WebGraphQlRequest} extension for
 * server handling of GraphQL over WebSocket requests.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebSocketGraphQlRequest extends WebGraphQlRequest {

	private final WebSocketSessionInfo sessionInfo;


	/**
	 * Create an instance.
	 * @param uri the URL for the HTTP request or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param cookies the HTTP request cookies
	 * @param remoteAddress the client remote address
	 * @param attributes session attributes
	 * @param body the deserialized content of the GraphQL request
	 * @param id the id from the GraphQL over WebSocket {@code "subscribe"} message
	 * @param locale the locale from the HTTP request, if any
	 * @param sessionInfo the WebSocket session id
	 * @since 1.3.0
	 */
	public WebSocketGraphQlRequest(
			URI uri, HttpHeaders headers, @Nullable MultiValueMap<String, HttpCookie> cookies,
			@Nullable InetSocketAddress remoteAddress, Map<String, Object> attributes, Map<String, Object> body,
			String id, @Nullable Locale locale, WebSocketSessionInfo sessionInfo) {

		super(uri, headers, cookies, remoteAddress, attributes, body, id, locale);
		Assert.notNull(sessionInfo, "WebSocketSessionInfo is required");
		this.sessionInfo = sessionInfo;
	}


	/**
	 * Return information about the underlying WebSocket session.
	 */
	public WebSocketSessionInfo getSessionInfo() {
		return this.sessionInfo;
	}

}

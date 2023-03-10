/*
 * Copyright 2020-2023 the original author or authors.
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


import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
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
	 * @deprecated as of 1.1.3 in favor of the constructor with cookies
	 */
	@Deprecated
	public WebSocketGraphQlRequest(
			URI uri, HttpHeaders headers, Map<String, Object> body, String id, @Nullable Locale locale,
			WebSocketSessionInfo sessionInfo) {

		this(uri, headers, null, body, id, locale, sessionInfo);
	}

	/**
	 * Create an instance.
	 * @param uri the URL for the HTTP request or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param cookies the request cookies
	 * @param body the deserialized content of the GraphQL request
	 * @param id the id from the GraphQL over WebSocket {@code "subscribe"} message
	 * @param locale the locale from the HTTP request, if any
	 * @param sessionInfo the WebSocket session id
	 * @since 1.1.3
	 */
	public WebSocketGraphQlRequest(
			URI uri, HttpHeaders headers, @Nullable MultiValueMap<String, HttpCookie> cookies,
			Map<String, Object> body, String id, @Nullable Locale locale, WebSocketSessionInfo sessionInfo) {

		super(uri, headers, cookies, body, id, locale);
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

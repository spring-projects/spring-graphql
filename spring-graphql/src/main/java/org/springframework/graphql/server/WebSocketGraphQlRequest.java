/*
 * Copyright 2020-2022 the original author or authors.
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

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;


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
	 * @param body the deserialized content of the GraphQL request
	 * @param id the id from the GraphQL over WebSocket {@code "subscribe"} message
	 * @param locale the locale from the HTTP request, if any
	 * @param sessionInfo the WebSocket session id
	 */
	public WebSocketGraphQlRequest(
			URI uri, HttpHeaders headers, Map<String, Object> body, String id, @Nullable Locale locale,
			WebSocketSessionInfo sessionInfo) {

		super(uri, headers, body, id, locale);
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

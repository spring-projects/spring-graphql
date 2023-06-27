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

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * {@link org.springframework.graphql.GraphQlRequest} implementation for server
 * handling over HTTP or WebSocket. Provides access to the URL and headers of
 * the underlying request. For WebSocket, these are the URL and headers of the
 * HTTP handshake request.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebGraphQlRequest extends DefaultExecutionGraphQlRequest implements ExecutionGraphQlRequest {

	private final UriComponents uri;

	private final HttpHeaders headers;


	/**
	 * Create an instance.
	 * @param uri the URL for the HTTP request or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param body the deserialized content of the GraphQL request
	 * @param id an identifier for the GraphQL request
	 * @param locale the locale from the HTTP request, if any
	 */
	public WebGraphQlRequest(
			URI uri, HttpHeaders headers, Map<String, Object> body, String id, @Nullable Locale locale) {

		super(getQuery(body), getOperation(body),
				getMap(VARIABLES_KEY, body), getMap(EXTENSIONS_KEY, body), id, locale);

		Assert.notNull(uri, "URI is required'");
		Assert.notNull(headers, "HttpHeaders is required'");

		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
	}

	private static String getQuery(Map<String, Object> body) {
		Object value = body.get(QUERY_KEY);
		if (!(value instanceof String) || !StringUtils.hasText((String) value)) {
			throw new ServerWebInputException("Invalid value for '" + QUERY_KEY + "'");
		}
		return (String) value;
	}

	@Nullable
	private static String getOperation(Map<String, Object> body) {
		Object value = body.get(OPERATION_NAME_KEY);
		if (value != null && !(value instanceof String)) {
			throw new ServerWebInputException("Invalid value for '" + OPERATION_NAME_KEY + "'");
		}
		return (String) value;
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private static Map<String, Object> getMap(String key, Map<String, Object> body) {
		Object value = body.get(key);
		if (value != null && !(value instanceof Map)) {
			throw new ServerWebInputException("Invalid value for '" + key + "'");
		}
		return (Map<String, Object>) value;
	}


	/**
	 * Return the URL for the HTTP request or WebSocket handshake.
	 */
	public UriComponents getUri() {
		return this.uri;
	}

	/**
	 * Return the HTTP headers of the request or WebSocket handshake.
	 */
	public HttpHeaders getHeaders() {
		return this.headers;
	}

}

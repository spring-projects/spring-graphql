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

		super(getKey("query", body), getKey("operationName", body), getKey("variables", body),
				getKey("extensions", body), id, locale);

		Assert.notNull(uri, "URI is required'");
		Assert.notNull(headers, "HttpHeaders is required'");

		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
	}

	/**
	 * Create an instance.
	 * @param uri the URL for the HTTP request
	 * @param headers the HTTP request headers
	 * @param query GraphQL's query
	 * @param operationName GraphQL's operation name
	 * @param variables GraphQL's variables map
	 * @param extensions GraphQL's extensions map
	 * @param id an identifier for the GraphQL request
	 * @param locale the locale from the HTTP request, if any
	 */
	public WebGraphQlRequest(
		URI uri, HttpHeaders headers,
		String query,
		String operationName,
		Map<String, Object> variables,
		Map<String, Object> extensions,
		String id, @Nullable Locale locale) {

		super(query, operationName, variables, extensions, id, locale);

		Assert.notNull(uri, "URI is required'");
		Assert.notNull(headers, "HttpHeaders is required'");

		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
	}

	@SuppressWarnings("unchecked")
	private static <T> T getKey(String key, Map<String, Object> body) {
		if (key.equals("query") && !StringUtils.hasText((String) body.get(key))) {
			throw new ServerWebInputException("No \"query\" in the request document");
		}
		return (T) body.get(key);
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

/*
 * Copyright 2020-2024 the original author or authors.
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
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
public class WebGraphQlRequest extends DefaultExecutionGraphQlRequest {

	private static final MultiValueMap<String, HttpCookie> EMPTY_COOKIES =
			CollectionUtils.unmodifiableMultiValueMap(new LinkedMultiValueMap<>());


	private final UriComponents uri;

	private final HttpHeaders headers;

	private final MultiValueMap<String, HttpCookie> cookies;

	private final @Nullable InetSocketAddress remoteAddress;

	private final Map<String, Object> attributes;

	/**
	 * Create an instance.
	 * @param uri the URL for the HTTP request or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param cookies the HTTP request cookies
	 * @param remoteAddress the HTTP client remote address
	 * @param attributes request attributes
	 * @param body the deserialized content of the GraphQL request
	 * @param id an identifier for the GraphQL request
	 * @param locale the locale from the HTTP request, if any
	 * @since 1.3.0
	 */
	public WebGraphQlRequest(
			URI uri, HttpHeaders headers, @Nullable MultiValueMap<String, HttpCookie> cookies,
			@Nullable InetSocketAddress remoteAddress, Map<String, Object> attributes,
			GraphQlRequest body, String id, @Nullable Locale locale) {

		this(uri, headers, cookies, remoteAddress, attributes, body.getDocument(),
				body.getOperationName(), body.getVariables(), body.getExtensions(), id, locale);
	}

	/**
	 * Constructor variant with a Map for the request body.
	 * @param uri the URL for the HTTP request or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param cookies the HTTP request cookies
	 * @param remoteAddress the HTTP client remote address
	 * @param attributes request attributes
	 * @param body the deserialized content of the GraphQL request
	 * @param id an identifier for the GraphQL request
	 * @param locale the locale from the HTTP request, if any
	 * @since 1.3.0
	 */
	public WebGraphQlRequest(
			URI uri, HttpHeaders headers, @Nullable MultiValueMap<String, HttpCookie> cookies,
			@Nullable InetSocketAddress remoteAddress, Map<String, Object> attributes,
			Map<String, Object> body, String id, @Nullable Locale locale) {

		this(uri, headers, cookies, remoteAddress, attributes, getQuery(body), getOperation(body),
				getMap(VARIABLES_KEY, body), getMap(EXTENSIONS_KEY, body), id, locale);
	}

	/**
	 * Create an instance.
	 * @param uri the URL for the HTTP request or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param cookies the HTTP request cookies
	 * @param attributes request attributes
	 * @param body the deserialized content of the GraphQL request
	 * @param id an identifier for the GraphQL request
	 * @param locale the locale from the HTTP request, if any
	 * @since 1.2.5
	 * @deprecated since 1.3.0 in favor {@link #WebGraphQlRequest(URI, HttpHeaders, MultiValueMap, InetSocketAddress, Map, Map, String, Locale)}
	 */
	@Deprecated(since = "1.3.0", forRemoval = true)
	public WebGraphQlRequest(
			URI uri, HttpHeaders headers, @Nullable MultiValueMap<String, HttpCookie> cookies,
			Map<String, Object> attributes, GraphQlRequest body, String id, @Nullable Locale locale) {

		this(uri, headers, cookies, null, attributes, body.getDocument(),
				body.getOperationName(), body.getVariables(), body.getExtensions(), id, locale);
	}

	/**
	 * Create an instance.
	 * @param uri the URL for the HTTP request or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param cookies the HTTP request cookies
	 * @param attributes request attributes
	 * @param body the deserialized content of the GraphQL request
	 * @param id an identifier for the GraphQL request
	 * @param locale the locale from the HTTP request, if any
	 * @since 1.1.3
	 * @deprecated since 1.3.0 in favor {@link #WebGraphQlRequest(URI, HttpHeaders, MultiValueMap, InetSocketAddress, Map, Map, String, Locale)}
	 */
	@Deprecated(since = "1.3.0", forRemoval = true)
	public WebGraphQlRequest(
			URI uri, HttpHeaders headers, @Nullable MultiValueMap<String, HttpCookie> cookies,
			Map<String, Object> attributes, Map<String, Object> body, String id, @Nullable Locale locale) {

		this(uri, headers, cookies, null, attributes, getQuery(body), getOperation(body),
				getMap(VARIABLES_KEY, body), getMap(EXTENSIONS_KEY, body), id, locale);
	}

	private static String getQuery(Map<String, Object> body) {
		Object value = body.get(QUERY_KEY);
		if (!(value instanceof String query) || !StringUtils.hasText(query)) {
			throw new ServerWebInputException("Invalid value for '" + QUERY_KEY + "'");
		}
		return (String) value;
	}

	private static @Nullable String getOperation(Map<String, Object> body) {
		Object value = body.get(OPERATION_NAME_KEY);
		if (value != null && !(value instanceof String)) {
			throw new ServerWebInputException("Invalid value for '" + OPERATION_NAME_KEY + "'");
		}
		return (String) value;
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Map<String, Object> getMap(String key, Map<String, Object> body) {
		Object value = body.get(key);
		if (value != null && !(value instanceof Map)) {
			throw new ServerWebInputException("Invalid value for '" + key + "'");
		}
		return (Map<String, Object>) value;
	}

	private WebGraphQlRequest(
			URI uri, HttpHeaders headers, @Nullable MultiValueMap<String, HttpCookie> cookies,
			@Nullable InetSocketAddress remoteAddress, Map<String, Object> attributes,
			String document, @Nullable String operationName,
			@Nullable Map<String, Object> variables, @Nullable Map<String, Object> extensions,
			String id, @Nullable Locale locale) {

		super(document, operationName, variables, extensions, id, locale);

		Assert.notNull(uri, "URI is required'");
		Assert.notNull(headers, "HttpHeaders is required'");

		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
		this.cookies = (cookies != null) ? CollectionUtils.unmodifiableMultiValueMap(cookies) : EMPTY_COOKIES;
		this.remoteAddress = remoteAddress;
		this.attributes = Collections.unmodifiableMap(attributes);
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

	/**
	 * Return the cookies of the request of WebSocket handshake.
	 * @since 1.1.3
	 */
	public MultiValueMap<String, HttpCookie> getCookies() {
		return this.cookies;
	}

	/**
	 * Return the remote address of the client, if available.
	 * @since 1.3.0
	 */
	public @Nullable InetSocketAddress getRemoteAddress() {
		return this.remoteAddress;
	}

	/**
	 * Return the request or WebSocket session attributes.
	 * @since 1.1.3
	 */
	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

}

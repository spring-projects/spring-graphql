/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.web;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.springframework.graphql.RequestInput;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Container for the input of a GraphQL query over HTTP. The input includes the
 * {@link UriComponents URL} and the headers of the request, as well as the query name,
 * operation name, and variables from the request body.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class WebInput extends RequestInput {

	private final UriComponents uri;

	private final HttpHeaders headers;

	private final String id;

	/**
	 * Create an instance.
	 * @param uri the url for the HTTP request, or WebSocket handshake
	 * @param headers the HTTP request headers
	 * @param body the content of the request deserialized from JSON
	 * @param locale the locale associated with the request, if any
	 * @param id an identifier for the GraphQL request, e.g. a subscription id for
	 * correlating request and response messages, or it could be an id associated with the
	 * underlying request/connection id, if available
	 */
	public WebInput(
			URI uri, HttpHeaders headers, Map<String, Object> body,
			@Nullable Locale locale, @Nullable String id) {

		super(getKey("query", body), getKey("operationName", body), getKey("variables", body), locale);
		Assert.notNull(uri, "URI is required'");
		Assert.notNull(headers, "HttpHeaders is required'");
		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
		this.id = (id != null) ? id : ObjectUtils.identityToString(this);
	}

	@SuppressWarnings("unchecked")
	private static <T> T getKey(String key, Map<String, Object> body) {
		if (key.equals("query") && !StringUtils.hasText((String) body.get(key))) {
			throw new ServerWebInputException("Query is required");
		}
		return (T) body.get(key);
	}


	/**
	 * Return the URI of the HTTP request including {@link UriComponents#getQueryParams()
	 * URL query parameters}.
	 * @return the HTTP request URI
	 */
	public UriComponents getUri() {
		return this.uri;
	}

	/**
	 * Return the headers of the request.
	 * @return the HTTP request headers
	 */
	public HttpHeaders getHeaders() {
		return this.headers;
	}

	/**
	 * Return the identifier for the request, which may be a subscription id for
	 * correlating request and response messages, or the underlying request or connection
	 * id, when available, or otherwise it's an
	 * {@link ObjectUtils#identityToString(Object) identity} hash based this
	 * {@code WebInput} instance.
	 * @return the HTTP request identifier
	 */
	public String getId() {
		return this.id;
	}

}

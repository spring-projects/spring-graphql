/*
 * Copyright 2020-2020 the original author or authors.
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
package org.springframework.graphql;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import graphql.ExecutionInput;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Container for the input of a GraphQL query over HTTP. The input includes the
 * {@link UriComponents URL} and the headers of the request, as well as the
 * query name, operation name, and variables from the request body.
 */
public class WebInput {

	static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {};

	static final ResolvableType MAP_RESOLVABLE_TYPE = ResolvableType.forType(MAP_PARAMETERIZED_TYPE_REF);


	private final UriComponents uri;

	private final HttpHeaders headers;

	private final String query;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;


	@SuppressWarnings("unchecked")
	public WebInput(URI uri, HttpHeaders headers, Map<String, Object> body) {
		Assert.notNull(uri, "URI is required'");
		Assert.notNull(body, "HttpHeaders is required'");
		Assert.notNull(body, "'body' is required'");
		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
		this.query = getAndValidateQuery(body);
		this.operationName = (String) body.get("operationName");
		this.variables = (Map<String, Object>) (body.get("variables") != null ? body.get("variables"): Collections.emptyMap());
	}

	private static String getAndValidateQuery(Map<String, Object> body) {
		String query = (String) body.get("query");
		if (!StringUtils.hasText(query)) {
			throw new ServerWebInputException("Query is required");
		}
		return query;
	}


	/**
	 * Return the URI of the HTTP request including
	 * {@link UriComponents#getQueryParams() query parameters}.
	 */
	public UriComponents uri() {
		return this.uri;
	}

	/**
	 * Return the headers of the request.
	 */
	public HttpHeaders headers() {
		return this.headers;
	}

	/**
	 * Return the query name extracted from the request body. This is guaranteed
	 * to be a non-empty string, or otherwise the request is rejected via
	 * {@link ServerWebInputException} as a 400 error.
	 */
	public String query() {
		return this.query;
	}

	/**
	 * Return the query operation name extracted from the request body or
	 * {@code null} if not provided.
	 */
	@Nullable
	public String operationName() {
		return this.operationName;
	}

	/**
	 * Return the query variables that can be referenced via $syntax extracted
	 * from the request body or a {@code null} if not provided.
	 */
	public Map<String, Object> variables() {
		return this.variables;
	}

	/**
	 * Create an {@link ExecutionInput} initialized with the {@link #query()},
	 * {@link #operationName()}, and {@link #variables()}.
	 */
	public ExecutionInput toExecutionInput() {
		return ExecutionInput.newExecutionInput()
				.query(query())
				.operationName(operationName())
				.variables(variables())
				.build();
	}

	@Override
	public String toString() {
		return "Query='" + query() + "'" +
				(operationName() != null ? ", Operation='" + operationName() + "'" : "") +
				(!CollectionUtils.isEmpty(variables()) ?  ", Variables=" + variables() : "");
	}
}
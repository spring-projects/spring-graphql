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
import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Represents the input to a GraphQL HTTP endpoint including URI, headers, and
 * the query, operationName, and variables from the request body.
 */
public class WebInput {

	static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final UriComponents uri;

	private final HttpHeaders headers;

	private final String query;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;


	@SuppressWarnings("unchecked")
	public WebInput(URI uri, HttpHeaders headers, Map<String, Object> body) {
		this.uri = UriComponentsBuilder.fromUri(uri).build(true);
		this.headers = headers;
		this.query = getAndValidateQuery(body);
		this.operationName = (String) body.get("operationName");
		this.variables = (Map<String, Object>) body.getOrDefault("variables", Collections.emptyMap());
	}

	private static String getAndValidateQuery(Map<String, Object> body) {
		String query = (String) body.get("query");
		if (!StringUtils.hasText(query)) {
			throw new ServerWebInputException("Query is required");
		}
		return query;
	}


	public UriComponents uri() {
		return this.uri;
	}

	public HttpHeaders headers() {
		return this.headers;
	}

	public String query() {
		return this.query;
	}

	@Nullable
	public String operationName() {
		return this.operationName;
	}

	public Map<String, Object> variables() {
		return this.variables;
	}

	public ExecutionInput toExecutionInput() {
		return ExecutionInput.newExecutionInput()
				.query(query())
				.operationName(operationName())
				.variables(variables())
				.build();
	}

}
/*
 * Copyright 2002-2021 the original author or authors.
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import graphql.ExecutionInput;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Container for a GraphQL request.
 */
public class RequestInput {

	private final String query;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;


	public RequestInput(String query, @Nullable String operationName, @Nullable Map<String, Object> vars) {
		Assert.notNull(query, "'query' is required");
		this.query = query;
		this.operationName = operationName;
		this.variables = (vars != null ? vars : Collections.emptyMap());
	}

	public RequestInput(Map<String, Object> body) {
		this(getKey("query", body), getKey("operationName", body), getKey("variables", body));
	}

	@SuppressWarnings("unchecked")
	private static <T> T getKey(String key, Map<String, Object> body) {
		return (T) body.get(key);
	}


	/**
	 * Return the query name extracted from the request body. This is guaranteed
	 * to be a non-empty string.
	 */
	public String getQuery() {
		return this.query;
	}

	/**
	 * Return the query operation name extracted from the request body or
	 * {@code null} if not provided.
	 */
	@Nullable
	public String getOperationName() {
		return this.operationName;
	}

	/**
	 * Return the query variables that can be referenced via $syntax extracted
	 * from the request body or a {@code null} if not provided.
	 */
	public Map<String, Object> getVariables() {
		return this.variables;
	}


	/**
	 * Create an {@link ExecutionInput} initialized with the {@link #getQuery()},
	 * {@link #getOperationName()}, and {@link #getVariables()}.
	 */
	public ExecutionInput toExecutionInput() {
		return ExecutionInput.newExecutionInput()
				.query(getQuery())
				.operationName(getOperationName())
				.variables(getVariables())
				.build();
	}

	/**
	 * Return a Map representation of the request input.
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("query", getQuery());
		if (getOperationName() != null) {
			map.put("operationName", getOperationName());
		}
		if (CollectionUtils.isEmpty(getVariables())) {
			map.put("variables", new LinkedHashMap<>(getVariables()));
		}
		return map;
	}


	@Override
	public String toString() {
		return "Query='" + getQuery() + "'" +
				(getOperationName() != null ? ", Operation='" + getOperationName() + "'" : "") +
				(!CollectionUtils.isEmpty(getVariables()) ?  ", Variables=" + getVariables() : "");
	}

}

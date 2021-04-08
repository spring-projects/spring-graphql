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
import java.util.Map;

import graphql.ExecutionInput;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;

/**
 * Container for a GraphQL request.
 */
public class RequestInput {

	protected final String query;

	@Nullable
	protected final String operationName;

	protected final Map<String, Object> variables;


	@SuppressWarnings("unchecked")
	public RequestInput(Map<String, Object> body) {
		Assert.notNull(body, "'body' is required'");
		this.query = getAndValidateQuery(body);
		this.operationName = (String) body.get("operationName");
		this.variables = (body.get("variables") != null ?
				(Map<String, Object>) body.get("variables") : Collections.emptyMap());
	}

	private static String getAndValidateQuery(Map<String, Object> body) {
		String query = (String) body.get("query");
		if (!StringUtils.hasText(query)) {
			throw new ServerWebInputException("Query is required");
		}
		return query;
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

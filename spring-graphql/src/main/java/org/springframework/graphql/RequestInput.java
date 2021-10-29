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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import graphql.ExecutionInput;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Common representation for GraphQL request input. This can be converted to
 * {@link ExecutionInput} via {@link #toExecutionInput()} and the
 * {@code ExecutionInput} further customized via
 * {@link #configureExecutionInput(BiFunction)}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class RequestInput {

	private final String query;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;

	@Nullable
	private final Locale locale;

	private final List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers = new ArrayList<>();


	public RequestInput(
			String query, @Nullable String operationName, @Nullable Map<String, Object> vars,
			@Nullable Locale locale) {

		Assert.notNull(query, "'query' is required");
		this.query = query;
		this.operationName = operationName;
		this.variables = ((vars != null) ? vars : Collections.emptyMap());
		this.locale = locale;
	}


	@SuppressWarnings("unchecked")
	private static <T> T getKey(String key, Map<String, Object> body) {
		return (T) body.get(key);
	}

	/**
	 * Return the query name extracted from the request. This is guaranteed to
	 * be a non-empty string.
	 * @return the query name
	 */
	public String getQuery() {
		return this.query;
	}

	/**
	 * Return the operation name extracted from the request or {@code null} if
	 * not provided.
	 * @return the operation name or {@code null}
	 */
	@Nullable
	public String getOperationName() {
		return this.operationName;
	}

	/**
	 * Return the variables that can be referenced via $syntax extracted from
	 * the request body or a {@code null} if not provided.
	 * @return the request variables or {@code null}
	 */
	public Map<String, Object> getVariables() {
		return this.variables;
	}

	/**
	 * Return the locale associated with the request, if available.
	 * @return the locale of {@code null}
	 */
	@Nullable
	public Locale getLocale() {
		return this.locale;
	}

	/**
	 * Provide a consumer to configure the {@link ExecutionInput} used for input to
	 * {@link graphql.GraphQL#executeAsync(ExecutionInput)}. The builder is initially
	 * populated with the values from {@link #getQuery()}, {@link #getOperationName()},
	 * and {@link #getVariables()}.
	 * @param configurer a {@code BiFunction} with the current {@code ExecutionInput} and
	 * a builder to modify it.
	 */
	public void configureExecutionInput(BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer) {
		this.executionInputConfigurers.add(configurer);
	}

	/**
	 * Create the {@link ExecutionInput} for request execution. This is initially
	 * populated from {@link #getQuery()}, {@link #getOperationName()}, and
	 * {@link #getVariables()}, and is then further customized through
	 * {@link #configureExecutionInput(BiFunction)}.
	 * @return the execution input
	 */
	public ExecutionInput toExecutionInput() {
		ExecutionInput executionInput = ExecutionInput.newExecutionInput()
				.query(this.query)
				.operationName(this.operationName)
				.variables(this.variables)
				.locale(this.locale)
				.build();

		for (BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer : this.executionInputConfigurers) {
			ExecutionInput current = executionInput;
			executionInput = executionInput.transform((builder) -> configurer.apply(current, builder));
		}

		return executionInput;
	}

	/**
	 * Return a Map representation of the request input.
	 * @return map representation of the input
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>(3);
		map.put("query", getQuery());
		if (getOperationName() != null) {
			map.put("operationName", getOperationName());
		}
		if (!CollectionUtils.isEmpty(getVariables())) {
			map.put("variables", new LinkedHashMap<>(getVariables()));
		}
		return map;
	}

	@Override
	public String toString() {
		return "Query='" + getQuery() + "'" +
				((getOperationName() != null) ? ", Operation='" + getOperationName() + "'" : "") +
				(!CollectionUtils.isEmpty(getVariables()) ? ", Variables=" + getVariables() : "") +
				(getLocale() != null ? ", Locale=" + getLocale() : "");
	}

}

/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Wraps an {@link ExecutionResult} and also exposes the {@link ExecutionInput}
 * prepared for the request.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class RequestOutput implements ExecutionResult {

	private final ExecutionInput executionInput;

	private final ExecutionResult executionResult;


	/**
	 * Create an instance.
	 * @param executionInput the input prepared for the request
	 * @param executionResult the result from performing the request
	 */
	public RequestOutput(ExecutionInput executionInput, ExecutionResult executionResult) {
		Assert.notNull(executionInput, "ExecutionInput is required.");
		Assert.notNull(executionResult, "ExecutionResult is required.");
		this.executionInput = executionInput;
		this.executionResult = executionResult;
	}


	/**
	 * Return the {@link ExecutionInput} that was prepared from the
	 * {@link RequestInput} and passed to {@link graphql.GraphQL}.
	 */
	public ExecutionInput getExecutionInput() {
		return this.executionInput;
	}

	@Nullable
	@Override
	public <T> T getData() {
		return this.executionResult.getData();
	}

	@Override
	public boolean isDataPresent() {
		return this.executionResult.isDataPresent();
	}

	public List<GraphQLError> getErrors() {
		return this.executionResult.getErrors();
	}

	@Nullable
	public Map<Object, Object> getExtensions() {
		return this.executionResult.getExtensions();
	}

	@Override
	public Map<String, Object> toSpecification() {
		return this.executionResult.toSpecification();
	}

	@Override
	public String toString() {
		return this.executionResult.toString();
	}

}

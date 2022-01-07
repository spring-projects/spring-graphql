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

import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * An {@link ExecutionResult} that also holds the {@link RequestInput}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class RequestOutput implements ExecutionResult {

	private final RequestInput requestInput;

	private final ExecutionResult executionResult;


	/**
	 * Create an instance that wraps the given {@link ExecutionResult}.
	 * @param requestInput the container for the GraphQL input
	 * @param executionResult the result of performing a graphql query
	 */
	public RequestOutput(RequestInput requestInput, ExecutionResult executionResult) {
		Assert.notNull(requestInput, "RequestInput is required.");
		Assert.notNull(executionResult, "ExecutionResult is required.");
		this.requestInput = requestInput;
		this.executionResult = executionResult;
	}

	/**
	 * Return the associated {@link RequestInput} used for the execution.
	 * @return the associated WebInput
	 */
	public RequestInput getRequestInput() {
		return this.requestInput;
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

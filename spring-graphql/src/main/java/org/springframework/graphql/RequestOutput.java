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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link GraphQlResponse} for server use that wraps the {@link ExecutionResult}
 * returned from {@link graphql.GraphQL} and also exposes the actual
 * {@link ExecutionInput} instance passed into it.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class RequestOutput implements GraphQlResponse {

	private final ExecutionInput input;

	private final ExecutionResult result;


	/**
	 * Constructor to create initial instance.
	 */
	public RequestOutput(ExecutionInput input, ExecutionResult result) {
		Assert.notNull(input, "ExecutionInput is required.");
		Assert.notNull(result, "ExecutionResult is required.");
		this.input = input;
		this.result = result;
	}

	/**
	 * Constructor to re-wrap from transport specific subclass.
	 */
	protected RequestOutput(RequestOutput requestOutput) {
		Assert.notNull(requestOutput, "RequestOutput is required.");
		this.input = requestOutput.getExecutionInput();
		this.result = requestOutput.result;
	}


	/**
	 * Return the {@link ExecutionInput} that was prepared from the
	 * {@link RequestInput} and passed to {@link graphql.GraphQL}.
	 */
	public ExecutionInput getExecutionInput() {
		return this.input;
	}

	protected ExecutionResult getExecutionResult() {
		return this.result;
	}

	@Override
	public boolean isValid() {
		return (this.result.isDataPresent() && this.result.getData() != null);
	}

	@Nullable
	@Override
	public <T> T getData() {
		return this.result.getData();
	}

	public List<GraphQLError> getErrors() {
		return this.result.getErrors();
	}

	public Map<Object, Object> getExtensions() {
		return (this.result.getExtensions() != null ? this.result.getExtensions() : Collections.emptyMap());
	}

	@Override
	public Map<String, Object> toMap() {
		return this.result.toSpecification();
	}

	@Override
	public String toString() {
		return this.result.toString();
	}

}

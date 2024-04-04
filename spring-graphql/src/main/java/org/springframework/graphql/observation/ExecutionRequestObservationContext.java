/*
 * Copyright 2020-2023 the original author or authors.
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

package org.springframework.graphql.observation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import io.micrometer.observation.Observation;

import org.springframework.lang.Nullable;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#EXECUTION_REQUEST GraphQL requests}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class ExecutionRequestObservationContext extends Observation.Context {

	private final ExecutionInput executionInput;

	@Nullable
	private ExecutionResult executionResult;

	public ExecutionRequestObservationContext(ExecutionInput executionInput) {
		this.executionInput = executionInput;
	}

	/**
	 * Return the {@link ExecutionInput input} for the request execution.
	 * @since 1.1.4
	 */
	public ExecutionInput getExecutionInput() {
		return this.executionInput;
	}

	/**
	 * Return the {@link ExecutionInput input} for the request execution.
	 * @deprecated since 1.1.4 in favor of {@link #getExecutionInput()}
	 */
	@Deprecated(since = "1.1.4", forRemoval = true)
	public ExecutionInput getCarrier() {
		return this.executionInput;
	}

	/**
	 * Return the {@link ExecutionResult result} for the request execution.
	 * @since 1.1.4
	 */
	@Nullable
	public ExecutionResult getExecutionResult() {
		return this.executionResult;
	}

	/**
	 * Set the {@link ExecutionResult result} for the request execution.
	 * @param executionResult the execution result
	 * @since 1.1.4
	 */
	public void setExecutionResult(ExecutionResult executionResult) {
		this.executionResult = executionResult;
	}

	/**
	 * Return the {@link ExecutionResult result} for the request execution.
	 * @deprecated since 1.1.4 in favor of {@link #getExecutionResult()}
	 */
	@Nullable
	@Deprecated(since = "1.1.4", forRemoval = true)
	public ExecutionResult getResponse() {
		return this.executionResult;
	}

}

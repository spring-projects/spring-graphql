/*
 * Copyright 2020-present the original author or authors.
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
import graphql.execution.ExecutionContext;
import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#EXECUTION_REQUEST GraphQL requests}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class ExecutionRequestObservationContext extends Observation.Context {

	private final ExecutionInput executionInput;

	private @Nullable ExecutionContext executionContext;

	private @Nullable ExecutionResult executionResult;

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
	 * Return the {@link ExecutionContext context} for the request execution.
	 * @since 1.3.7
	 */

	public @Nullable ExecutionContext getExecutionContext() {
		return this.executionContext;
	}

	/**
	 * Set the {@link ExecutionContext context} for the request execution.
	 * @param executionContext the execution context
	 * @since 1.3.7
	 */
	public void setExecutionContext(ExecutionContext executionContext) {
		this.executionContext = executionContext;
	}

	/**
	 * Return the {@link ExecutionResult result} for the request execution.
	 * @since 1.1.4
	 */
	public @Nullable ExecutionResult getExecutionResult() {
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

}

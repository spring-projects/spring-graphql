/*
 * Copyright 2020-2022 the original author or authors.
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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.graphql.observation.GraphQlObservationDocumentation.ExecutionRequestHighCardinalityKeyNames;
import org.springframework.graphql.observation.GraphQlObservationDocumentation.ExecutionRequestLowCardinalityKeyNames;

/**
 * Default implementation for a {@link ExecutionRequestObservationConvention}
 * extracting information from a {@link ExecutionRequestObservationContext}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class DefaultExecutionRequestObservationConvention implements ExecutionRequestObservationConvention {

	private static final String DEFAULT_NAME = "graphql.request";

	private static final String BASE_CONTEXTUAL_NAME = "graphQL ";

	private static final KeyValue OUTCOME_SUCCESS = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OUTCOME, "SUCCESS");

	private static final KeyValue OUTCOME_REQUEST_ERROR = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OUTCOME, "REQUEST_ERROR");

	private static final KeyValue OUTCOME_INTERNAL_ERROR = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OUTCOME, "INTERNAL_ERROR");

	private static final KeyValue OPERATION_QUERY = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OPERATION, "query");

	private final String name;

	public DefaultExecutionRequestObservationConvention() {
		this(DEFAULT_NAME);
	}

	public DefaultExecutionRequestObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getContextualName(ExecutionRequestObservationContext context) {
		return BASE_CONTEXTUAL_NAME + context.getCarrier().getOperationName();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ExecutionRequestObservationContext context) {
		return KeyValues.of(outcome(context), operation(context));
	}

	protected KeyValue outcome(ExecutionRequestObservationContext context) {
		if (context.getError() != null || context.getResponse() == null) {
			return OUTCOME_INTERNAL_ERROR;
		}
		else if (context.getResponse().getErrors().size() > 0) {
			return OUTCOME_REQUEST_ERROR;
		}
		return OUTCOME_SUCCESS;
	}

	protected KeyValue operation(ExecutionRequestObservationContext context) {
		String operationName = context.getCarrier().getOperationName();
		if (operationName != null) {
			return KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OPERATION, operationName);
		}
		return OPERATION_QUERY;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ExecutionRequestObservationContext context) {
		return KeyValues.of(executionId(context));
	}

	protected KeyValue executionId(ExecutionRequestObservationContext context) {
		return KeyValue.of(ExecutionRequestHighCardinalityKeyNames.EXECUTION_ID, context.getCarrier().getExecutionId().toString());
	}

}

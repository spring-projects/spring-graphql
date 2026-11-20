/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.observation;

import java.util.Locale;

import graphql.language.OperationDefinition;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.graphql.execution.ErrorType;
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

	private static final String BASE_CONTEXTUAL_NAME = "graphql ";

	private static final KeyValue OUTCOME_SUCCESS = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OUTCOME, "SUCCESS");

	private static final KeyValue OUTCOME_REQUEST_ERROR = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OUTCOME, "REQUEST_ERROR");

	private static final KeyValue OUTCOME_INTERNAL_ERROR = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OUTCOME, "INTERNAL_ERROR");

	private static final KeyValue OPERATION_NAME_QUERY = KeyValue.of(ExecutionRequestHighCardinalityKeyNames.OPERATION_NAME, "query");

	private static final KeyValue OPERATION_TYPE_DEFAULT = KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OPERATION_TYPE, "operation");

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
		return BASE_CONTEXTUAL_NAME + operationType(context).getValue();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ExecutionRequestObservationContext context) {
		return KeyValues.of(outcome(context), operationType(context));
	}

	protected KeyValue outcome(ExecutionRequestObservationContext context) {
		if (context.getError() != null || context.getExecutionResult() == null) {
			return OUTCOME_INTERNAL_ERROR;
		}
		else if (!context.getExecutionResult().getErrors().isEmpty()) {
			if (context.getExecutionResult().getErrors().stream().anyMatch((error) ->  ErrorType.INTERNAL_ERROR.equals(error.getErrorType()))) {
				return OUTCOME_INTERNAL_ERROR;
			}
			return OUTCOME_REQUEST_ERROR;
		}
		return OUTCOME_SUCCESS;
	}

	protected KeyValue operationType(ExecutionRequestObservationContext context) {
		if (context.getExecutionContext() != null) {
			OperationDefinition.Operation operation = context.getExecutionContext().getOperationDefinition().getOperation();
			return KeyValue.of(ExecutionRequestLowCardinalityKeyNames.OPERATION_TYPE, operation.name().toLowerCase(Locale.ROOT));
		}
		return OPERATION_TYPE_DEFAULT;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ExecutionRequestObservationContext context) {
		return KeyValues.of(executionId(context), operationName(context));
	}

	protected KeyValue executionId(ExecutionRequestObservationContext context) {
		return KeyValue.of(ExecutionRequestHighCardinalityKeyNames.EXECUTION_ID, context.getExecutionInput().getExecutionIdNonNull().toString());
	}

	protected KeyValue operationName(ExecutionRequestObservationContext context) {
		String operationName = context.getExecutionInput().getOperationName();
		if (operationName != null) {
			return KeyValue.of(ExecutionRequestHighCardinalityKeyNames.OPERATION_NAME, operationName);
		}
		return OPERATION_NAME_QUERY;
	}

}

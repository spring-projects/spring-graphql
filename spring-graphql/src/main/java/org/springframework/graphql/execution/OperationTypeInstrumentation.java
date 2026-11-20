/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.util.Set;

import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.OperationDefinition;
import org.jspecify.annotations.Nullable;

import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;


/**
 * Instrumentation that checks whether the current request is allowed to be executed by
 * looking at its operation type. The set of allowed operation types for the current
 * request is enforced by the transport layer semantics.
 * @author Brian Clozel
 */
class OperationTypeInstrumentation extends SimplePerformantInstrumentation {

	@Override
	public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(
			InstrumentationExecuteOperationParameters parameters, @Nullable InstrumentationState state) {

		ExecutionContext context = parameters.getExecutionContext();
		Set<OperationDefinition.Operation> allowed = context.getGraphQLContext().get(DefaultExecutionGraphQlRequest.ALLOWED_OPERATIONS_KEY);
		if (allowed != null) {
			OperationDefinition.Operation operation = context.getOperationDefinition().getOperation();
			if (!allowed.contains(operation)) {
				throw new OperationNotAllowedException(operation, allowed);
			}
		}
		return super.beginExecuteOperation(parameters, state);
	}
}

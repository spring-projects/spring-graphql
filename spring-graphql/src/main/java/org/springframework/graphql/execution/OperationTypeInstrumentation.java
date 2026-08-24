/*
 * Copyright 2026-present the original author or authors.
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

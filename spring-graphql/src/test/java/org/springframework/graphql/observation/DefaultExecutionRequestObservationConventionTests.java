/*
 * Copyright 2020-2024 the original author or authors.
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

import java.util.function.Consumer;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionContextBuilder;
import graphql.execution.ExecutionId;
import graphql.language.OperationDefinition;
import graphql.schema.idl.errors.QueryOperationMissingError;
import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.execution.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultExecutionRequestObservationConvention}.
 *
 * @author Brian Clozel
 */
class DefaultExecutionRequestObservationConventionTests {

	DefaultExecutionRequestObservationConvention convention = new DefaultExecutionRequestObservationConvention();

	ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }")
			.executionId(ExecutionId.from("42"))
			.operationName("query").build();


	@Test
	void nameHasDefault() {
		assertThat(this.convention.getName()).isEqualTo("graphql.request");
	}

	@Test
	void nameCanBeCustomized() {
		String customName = "graphql.custom";
		DefaultExecutionRequestObservationConvention customConvention = new DefaultExecutionRequestObservationConvention(customName);
		assertThat(customConvention.getName()).isEqualTo(customName);
	}

	@Test
	void hasContextualName() {
		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }")
				.operationName("mutation").build();
		ExecutionRequestObservationContext context = createObservationContext(input);
		assertThat(this.convention.getContextualName(context)).isEqualTo("graphql mutation");
	}

	@Test
	void hasOperationKeyValueWhenSuccessfulOutput() {
		ExecutionRequestObservationContext context = createObservationContext(this.input);
		assertThat(this.convention.getHighCardinalityKeyValues(context)).contains(KeyValue.of("graphql.operation.name", "query"));
	}

	@Test
	void hasOutcomeKeyValueWhenSuccessfulOutput() {
		ExecutionRequestObservationContext context = createObservationContext(this.input);
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("graphql.outcome", "SUCCESS"));
	}

	@Test
	void hasOutcomeKeyValueWhenErrorOutput() {
		ExecutionRequestObservationContext context = createObservationContext(this.input,
				builder -> builder.addError(new QueryOperationMissingError()));
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("graphql.outcome", "REQUEST_ERROR"));
	}

	@Test
	void hasOutcomeKeyValueWhenInternalError() {
		ExecutionRequestObservationContext context = createObservationContext(this.input);
		GraphQLError graphQLError = GraphQLError.newError().errorType(ErrorType.INTERNAL_ERROR).message(ErrorType.INTERNAL_ERROR + " for [executionId]").build();
		context.setExecutionResult(ExecutionResult.newExecutionResult().addError(graphQLError).build());
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("graphql.outcome", "INTERNAL_ERROR"));
	}

	@Test
	void hasExecutionIdKeyValue() {
		ExecutionRequestObservationContext context = createObservationContext(this.input);
		assertThat(this.convention.getHighCardinalityKeyValues(context)).contains(KeyValue.of("graphql.execution.id", "42"));
	}

	private ExecutionRequestObservationContext createObservationContext(ExecutionInput executionInput) {
		return createObservationContext(executionInput, builder -> { });
	}

	private ExecutionRequestObservationContext createObservationContext(ExecutionInput executionInput, Consumer<ExecutionResultImpl.Builder> resultConsumer) {
		ExecutionRequestObservationContext context = new ExecutionRequestObservationContext(executionInput);
		ExecutionResultImpl.Builder builder = ExecutionResultImpl.newExecutionResult();
		resultConsumer.accept(builder);
		context.setExecutionResult(builder.build());

		ExecutionContext executionContext = ExecutionContextBuilder.newExecutionContextBuilder()
				.executionId(ExecutionId.from("123_456_789"))
				.executionInput(executionInput)
				.operationDefinition(OperationDefinition.newOperationDefinition().operation(OperationDefinition.Operation.MUTATION).build())
				.build();
		context.setExecutionContext(executionContext);
		return context;
	}

}

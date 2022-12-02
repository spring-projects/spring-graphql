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


import java.util.Map;

import graphql.ExecutionInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExecutionRequestObservationContext}.
 *
 * @author Brian Clozel
 */
class ExecutionRequestObservationContextTests {

	@Test
	void readPropagationFieldFromGraphQlContext() {
		ExecutionInput executionInput = ExecutionInput
				.newExecutionInput("{ notUsed }")
				.graphQLContext(builder -> builder.of("X-Tracing-Test", "traceId"))
				.build();
		ExecutionRequestObservationContext context = new ExecutionRequestObservationContext(executionInput);
		assertThat(context.getGetter().get(executionInput, "X-Tracing-Test")).isEqualTo("traceId");
	}

	@Test
	void readPropagationFieldFromExtensions() {
		ExecutionInput executionInput = ExecutionInput
				.newExecutionInput("{ notUsed }")
				.extensions(Map.of("X-Tracing-Test", "traceId"))
				.build();
		ExecutionRequestObservationContext context = new ExecutionRequestObservationContext(executionInput);
		assertThat(context.getGetter().get(executionInput, "X-Tracing-Test")).isEqualTo("traceId");
	}

	@Test
	void doesNotFailIsMissingPropagationField() {
		ExecutionInput executionInput = ExecutionInput
				.newExecutionInput("{ notUsed }")
				.build();
		ExecutionRequestObservationContext context = new ExecutionRequestObservationContext(executionInput);
		assertThat(context.getGetter().get(executionInput, "X-Tracing-Test")).isNull();
	}
}
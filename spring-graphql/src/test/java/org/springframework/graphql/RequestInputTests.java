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

package org.springframework.graphql;

import graphql.execution.ExecutionId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RequestInput}.
 *
 * @author Brian Clozel
 */
class RequestInputTests {

	private RequestInput requestInput = new RequestInput("greeting", "Greeting", null, null, "id");

	@Test
	void shouldUseCustomExecutionIdIfPresent() {
		ExecutionId customId = ExecutionId.from("customId");
		this.requestInput.executionId(customId);
		assertThat(this.requestInput.toExecutionInput(true).getExecutionId()).isEqualTo(customId);
		assertThat(this.requestInput.toExecutionInput(false).getExecutionId()).isEqualTo(customId);
	}

	@Test
	void executionIdShouldFallBackToRequestId() {
		assertThat(this.requestInput.toExecutionInput(true).getExecutionId()).isEqualTo(ExecutionId.from("id"));
	}

	@Test
	void executionIdShouldFallBackToProvider() {
		assertThat(this.requestInput.toExecutionInput(false).getExecutionId()).isNull();
	}
}
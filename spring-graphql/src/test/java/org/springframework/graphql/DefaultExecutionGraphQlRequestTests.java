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

import java.util.Locale;

import graphql.execution.ExecutionId;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultExecutionGraphQlRequest}.
 *
 * @author Brian Clozel
 */
class DefaultExecutionGraphQlRequestTests {

	private final DefaultExecutionGraphQlRequest request =
			new DefaultExecutionGraphQlRequest("greeting", "Greeting", null, null, "id", null);


	@Test
	void shouldUseRequestId() {
		assertThat(this.request.toExecutionInput().getExecutionId()).isEqualTo(ExecutionId.from("id"));
	}

	@Test
	void shouldUseExecutionId() {
		ExecutionId customId = ExecutionId.from("customId");
		this.request.executionId(customId);
		assertThat(this.request.toExecutionInput().getExecutionId()).isEqualTo(customId);
	}

	@Test
	void shouldHaveDefaultLocale() {
		assertThat(this.request.getLocale()).isEqualTo(Locale.getDefault());
	}

}
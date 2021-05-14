/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.boot.actuate.metrics;

import java.util.Arrays;

import graphql.ErrorType;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.test.tester.TestExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlTags}
 * 
 * @author Brian Clozel
 */
class GraphQlTagsTests {

	@Test
	void executionOutcomeShouldSucceed() {
		Tag outcomeTag = GraphQlTags.executionOutcome(new TestExecutionResult(), null);
		assertThat(outcomeTag.getValue()).isEqualTo("SUCCESS");
	}

	@Test
	void executionOutcomeShouldErrorWhenExceptionThrown() {
		Tag outcomeTag = GraphQlTags.executionOutcome(new TestExecutionResult(), new IllegalArgumentException("test error"));
		assertThat(outcomeTag.getValue()).isEqualTo("ERROR");
	}

	@Test
	void executionOutcomeShouldErrorWhenResponseErrors() {
		ExecutionResultImpl.Builder builder = new ExecutionResultImpl.Builder();
		builder.addError(GraphqlErrorBuilder.newError().message("Invalid query").build());
		Tag outcomeTag = GraphQlTags.executionOutcome(builder.build(), null);
		assertThat(outcomeTag.getValue()).isEqualTo("ERROR");
	}

	@Test
	void errorTypeShouldBeDefinedIfPresent() {
		GraphQLError error = GraphqlErrorBuilder.newError().errorType(ErrorType.DataFetchingException).message("test error").build();
		Tag errorTypeTag = GraphQlTags.errorType(error);
		assertThat(errorTypeTag.getValue()).isEqualTo("DataFetchingException");
	}

	@Test
	void errorPathShouldUseJsonPathFormat() {
		GraphQLError error = GraphqlErrorBuilder.newError().path(Arrays.asList("project", "name")).message("test error").build();
		Tag errorPathTag = GraphQlTags.errorPath(error);
		assertThat(errorPathTag.getValue()).isEqualTo("$.project.name");
	}

	@Test
	void errorPathShouldUseJsonPathFormatForIndices() {
		GraphQLError error = GraphqlErrorBuilder.newError().path(Arrays.asList("issues", "42", "title")).message("test error").build();
		Tag errorPathTag = GraphQlTags.errorPath(error);
		assertThat(errorPathTag.getValue()).isEqualTo("$.issues[*].title");
	}

	@Test
	void dataFetchingOutcomeShouldBeSuccessfulIfNoException() {
		Tag fetchingOutcomeTag = GraphQlTags.dataFetchingOutcome(null);
		assertThat(fetchingOutcomeTag.getValue()).isEqualTo("SUCCESS");
	}

	@Test
	void dataFetchingOutcomeShouldBeErrorIfException() {
		Tag fetchingOutcomeTag = GraphQlTags.dataFetchingOutcome(new IllegalStateException("error state"));
		assertThat(fetchingOutcomeTag.getValue()).isEqualTo("ERROR");
	}

}
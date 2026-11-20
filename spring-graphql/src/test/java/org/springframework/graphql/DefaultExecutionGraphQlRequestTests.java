/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import java.util.Locale;
import java.util.Set;

import graphql.execution.ExecutionId;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Test
	void shouldFailForEmptyAllowedOperations() {
		assertThatThrownBy(() -> request.allowedOperations(Set.of())).isInstanceOf(IllegalStateException.class);
	}

}

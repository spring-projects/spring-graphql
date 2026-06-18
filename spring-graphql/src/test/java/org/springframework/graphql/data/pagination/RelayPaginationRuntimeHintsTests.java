/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.data.pagination;

import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.AotServices;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RelayPaginationRuntimeHints}.
 *
 * @author Seonwoo Jung
 */
class RelayPaginationRuntimeHintsTests {

	private final RuntimeHints hints = new RuntimeHints();

	@Test
	void registrarIsRegisteredInAotFactories() {
		assertThat(AotServices.factories(getClass().getClassLoader()).load(RuntimeHintsRegistrar.class))
				.anyMatch(RelayPaginationRuntimeHints.class::isInstance);
	}

	@Test
	void registersReflectionHintsForRelayTypes() {
		new RelayPaginationRuntimeHints().registerHints(this.hints, getClass().getClassLoader());

		assertThat(RuntimeHintsPredicates.reflection()
				.onType(DefaultConnection.class)
				.withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(this.hints);
		assertThat(RuntimeHintsPredicates.reflection()
				.onType(DefaultConnectionCursor.class)
				.withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(this.hints);
		assertThat(RuntimeHintsPredicates.reflection()
				.onType(DefaultEdge.class)
				.withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(this.hints);
		assertThat(RuntimeHintsPredicates.reflection()
				.onType(DefaultPageInfo.class)
				.withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(this.hints);
	}

}

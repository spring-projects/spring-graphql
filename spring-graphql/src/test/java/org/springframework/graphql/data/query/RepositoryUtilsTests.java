/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.data.query;

import java.util.Collections;
import java.util.Map;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.CursorStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RepositoryUtils}.
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class RepositoryUtilsTests {

	private final CursorStrategy<ScrollPosition> cursorStrategy = RepositoryUtils.defaultCursorStrategy();

	private final ScrollSubrange defaultSubrange = ScrollSubrange.create(ScrollPosition.offset(50), 20, true);


	@Test
	void buildScrollSubrangeForward() {
		OffsetScrollPosition offset = ScrollPosition.offset(10);
		int count = 5;

		DataFetchingEnvironment env = environment(
				Map.of("first", count, "after", cursorStrategy.toCursor(offset)));

		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy, defaultSubrange);

		assertThat(range.position().get()).isEqualTo(offset);
		assertThat(range.count().getAsInt()).isEqualTo(count);
		assertThat(range.forward()).isTrue();
	}

	@Test
	void buildScrollSubrangeBackward() {
		OffsetScrollPosition offset = ScrollPosition.offset(10);
		int count = 5;

		DataFetchingEnvironment env = environment(
				Map.of("last", count, "before", cursorStrategy.toCursor(offset)));

		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy, defaultSubrange);

		assertThat(range.position().get()).isEqualTo(offset.advanceBy(-count-1));
		assertThat(range.count().getAsInt()).isEqualTo(count);
		assertThat(range.forward()).isTrue();
	}

	@Test
	void buildScrollSubrangeForwardWithDefaultScrollSubrange() {
		DataFetchingEnvironment env = environment(Collections.emptyMap());
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy, defaultSubrange);

		assertThat(range.position().get()).isEqualTo(getDefaultPosition());
		assertThat(range.count().getAsInt()).isEqualTo(this.defaultSubrange.count().getAsInt());
		assertThat(range.forward()).isTrue();
	}

	@Test // gh-900
	void buildScrollSubrangeBackwardFromDefaultPosition() {
		int count = 5;
		DataFetchingEnvironment env = environment(Map.of("last", count));
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy, defaultSubrange);

		assertThat(range.position().get()).isEqualTo(getDefaultPosition().advanceBy(-count-1));
		assertThat(range.count().getAsInt()).isEqualTo(count);
		assertThat(range.forward()).isTrue();
	}

	private static DataFetchingEnvironment environment(Map<String, Object> arguments) {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
				.arguments(arguments)
				.build();
	}

	private OffsetScrollPosition getDefaultPosition() {
		return (OffsetScrollPosition) defaultSubrange.position().get();
	}

}

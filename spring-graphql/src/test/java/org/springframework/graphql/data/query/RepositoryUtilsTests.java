/*
 * Copyright 2002-present the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.CursorStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RepositoryUtils}.
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
class RepositoryUtilsTests {

	private final CursorStrategy<ScrollPosition> cursorStrategy = RepositoryUtils.defaultCursorStrategy();


	@Test
	void forward() {
		OffsetScrollPosition pos = ScrollPosition.offset(10);
		int count = 5;
		DataFetchingEnvironment env = environment(Map.of("first", count, "after", cursorStrategy.toCursor(pos)));
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy);

		assertSubrange(true, count, pos, range);
	}

	@Test
	void backward() {
		OffsetScrollPosition pos = ScrollPosition.offset(10);
		int count = 5;
		DataFetchingEnvironment env = environment(Map.of("last", count, "before", cursorStrategy.toCursor(pos)));
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy);

		assertSubrange(true, count, ScrollPosition.offset(4), range);
	}

	@Test
	void forwardWithCountOnly() {
		int count = 5;
		DataFetchingEnvironment env = environment(Map.of("first", count));
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy);

		assertSubrange(true, count, null, range);
	}

	@Test
	void forwardWithPositionOnly() {
		OffsetScrollPosition pos = ScrollPosition.offset(10);
		DataFetchingEnvironment env = environment(Map.of("after", cursorStrategy.toCursor(pos)));
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy);

		assertSubrange(true, null, pos, range);
	}

	@Test
	void backwardWithCountOnly() {
		int count = 5;
		DataFetchingEnvironment env = environment(Map.of("last", count));
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy);

		assertSubrange(false, count, null, range);
	}

	@Test
	void backwardWithPositionOnly() {
		OffsetScrollPosition pos = ScrollPosition.offset(10);
		DataFetchingEnvironment env = environment(Map.of("before", cursorStrategy.toCursor(pos)));
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy);

		assertSubrange(true, null, pos.advanceBy(-2), range);
	}

	@Test
	void noInput() {
		DataFetchingEnvironment env = environment(Collections.emptyMap());
		ScrollSubrange range = RepositoryUtils.getScrollSubrange(env, cursorStrategy);

		assertSubrange(true, null, null, range);
	}

	private static DataFetchingEnvironment environment(Map<String, Object> arguments) {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
				.arguments(arguments)
				.build();
	}

	private static void assertSubrange(
			boolean forward, @Nullable Integer count, @Nullable ScrollPosition pos, ScrollSubrange subrange) {

		assertThat(subrange.forward()).isEqualTo(forward);

		if (count != null) {
			assertThat(subrange.count().orElse(0)).isEqualTo(count);
		}
		else {
			assertThat(subrange.count()).isNotPresent();
		}

		if (pos != null) {
			assertThat(subrange.position().get()).isEqualTo(pos);
		}
		else {
			assertThat(subrange.position()).isNotPresent();
		}
	}

}

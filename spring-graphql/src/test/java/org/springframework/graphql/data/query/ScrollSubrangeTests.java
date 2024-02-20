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

package org.springframework.graphql.data.query;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.ScrollPosition.Direction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScrollPositionCursorStrategy}.
 *
 * @author Rossen Stoyanchev
 * @author Oliver Drotbohm
 */
public class ScrollSubrangeTests {

	@Test
	void offsetForward() {
		int count = 10;
		ScrollSubrange subrange = ScrollSubrange.create(ScrollPosition.offset(30), count, true);

		assertThat(getOffset(subrange)).isEqualTo(31);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void offsetBackward() {
		int count = 10;
		ScrollSubrange subrange = ScrollSubrange.create(ScrollPosition.offset(30), count, false);

		assertThat(getOffset(subrange)).isEqualTo(20);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void keysetForward() {
		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);

		int count = 10;
		ScrollSubrange subrange = ScrollSubrange.create(ScrollPosition.forward(keys), count, true);

		KeysetScrollPosition actualPosition = (KeysetScrollPosition) subrange.position().get();
		assertThat(actualPosition.getKeys()).isEqualTo(keys);
		assertThat(actualPosition.getDirection()).isEqualTo(Direction.FORWARD);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void keysetBackward() {
		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);

		int count = 10;
		ScrollSubrange subrange = ScrollSubrange.create(ScrollPosition.forward(keys), count, false);

		KeysetScrollPosition actualPosition = (KeysetScrollPosition) subrange.position().get();
		assertThat(actualPosition.getKeys()).isEqualTo(keys);
		assertThat(actualPosition.getDirection()).isEqualTo(Direction.BACKWARD);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isFalse();
	}

	@Test
	void nullInput() {
		ScrollSubrange subrange = ScrollSubrange.create(null, null, true);

		assertThat(subrange.position()).isNotPresent();
		assertThat(subrange.count()).isNotPresent();
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void offsetBackwardWithInsufficientCount() {
		ScrollSubrange subrange = ScrollSubrange.create(ScrollPosition.offset(5), 10, false);

		assertThat(getOffset(subrange)).isEqualTo(0);
		assertThat(subrange.count().getAsInt()).isEqualTo(5);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void offsetBackwardFromInitialOffset() {
		ScrollSubrange subrange = ScrollSubrange.create(ScrollPosition.offset(0), 10, false);

		assertThat(getOffset(subrange)).isEqualTo(0);
		assertThat(subrange.count().getAsInt()).isEqualTo(0);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void offsetBackwardWithNullCount() {
		ScrollSubrange subrange = ScrollSubrange.create(ScrollPosition.offset(30), null, false);

		assertThat(getOffset(subrange)).isEqualTo(30);
		assertThat(subrange.count()).isNotPresent();
		assertThat(subrange.forward()).isTrue();
	}

	private static long getOffset(ScrollSubrange subrange) {
		return ((OffsetScrollPosition) subrange.position().get()).getOffset();
	}

}

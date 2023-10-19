/*
 * Copyright 2020-2023 the original author or authors.
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
	void offset() {
		ScrollPosition position = ScrollPosition.offset(30);
		int count = 10;

		ScrollSubrange subrange = ScrollSubrange.create(position, count, true);
		assertThat(((OffsetScrollPosition) subrange.position().get())).isEqualTo(position);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isTrue();

		subrange = ScrollSubrange.create(position, count, false);
		assertThat(((OffsetScrollPosition) subrange.position().get()).getOffset()).isEqualTo(19);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void keyset() {
		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);

		ScrollPosition position = ScrollPosition.forward(keys);
		int count = 10;

		ScrollSubrange subrange = ScrollSubrange.create(position, count, true);
		KeysetScrollPosition actualPosition = (KeysetScrollPosition) subrange.position().get();
		assertThat(actualPosition.getKeys()).isEqualTo(keys);
		assertThat(actualPosition.getDirection()).isEqualTo(Direction.FORWARD);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isTrue();

		subrange = ScrollSubrange.create(position, count, false);
		actualPosition = (KeysetScrollPosition) subrange.position().get();
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
	void offsetBackwardPaginationWithInsufficientCount() {
		ScrollPosition position = ScrollPosition.offset(5);
		ScrollSubrange subrange = ScrollSubrange.create(position, 10, false);

		assertThat(((OffsetScrollPosition) subrange.position().get()).getOffset()).isEqualTo(0);
		assertThat(subrange.count().getAsInt()).isEqualTo(4);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void offsetBackwardPaginationWithOffsetZero() {
		ScrollPosition position = ScrollPosition.offset(0);
		ScrollSubrange subrange = ScrollSubrange.create(position, 10, false);

		assertThat(((OffsetScrollPosition) subrange.position().get()).getOffset()).isEqualTo(0);
		assertThat(subrange.count().getAsInt()).isEqualTo(0);
		assertThat(subrange.forward()).isTrue();
	}

	@Test
	void offsetBackwardPaginationWithNullCount() {
		ScrollPosition position = ScrollPosition.offset(30);
		ScrollSubrange subrange = ScrollSubrange.create(position, null, false);

		assertThat(((OffsetScrollPosition) subrange.position().get())).isEqualTo(position);
		assertThat(subrange.count()).isNotPresent();
		assertThat(subrange.forward()).isTrue();
	}

}

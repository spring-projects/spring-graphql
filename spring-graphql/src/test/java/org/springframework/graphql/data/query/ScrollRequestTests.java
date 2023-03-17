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
import org.springframework.data.domain.KeysetScrollPosition.Direction;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScrollPositionCursorStrategy}.
 *
 * @author Rossen Stoyanchev
 */
public class ScrollRequestTests {

	@Test
	void offset() {
		OffsetScrollPosition position = OffsetScrollPosition.of(30);
		int count = 10;

		ScrollRequest request = new ScrollRequest(position, count, true);
		assertThat(((OffsetScrollPosition) request.position().get())).isEqualTo(position);
		assertThat(request.count().get()).isEqualTo(count);
		assertThat(request.forward()).isTrue();

		request = new ScrollRequest(position, count, false);
		assertThat(((OffsetScrollPosition) request.position().get()).getOffset()).isEqualTo(20);
		assertThat(request.count().get()).isEqualTo(count);
		assertThat(request.forward()).isTrue();
	}

	@Test
	void keyset() {
		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);

		ScrollPosition position = KeysetScrollPosition.of(keys);
		int count = 10;

		ScrollRequest request = new ScrollRequest(position, count, true);
		KeysetScrollPosition actualPosition = (KeysetScrollPosition) request.position().get();
		assertThat(actualPosition.getKeys()).isEqualTo(keys);
		assertThat(actualPosition.getDirection()).isEqualTo(Direction.Forward);
		assertThat(request.count().get()).isEqualTo(count);
		assertThat(request.forward()).isTrue();

		request = new ScrollRequest(position, count, false);
		actualPosition = (KeysetScrollPosition) request.position().get();
		assertThat(actualPosition.getKeys()).isEqualTo(keys);
		assertThat(actualPosition.getDirection()).isEqualTo(Direction.Backward);
		assertThat(request.count().get()).isEqualTo(count);
		assertThat(request.forward()).isFalse();
	}

	@Test
	void nullInput() {
		ScrollRequest request = new ScrollRequest(null, null, true);

		assertThat(request.position()).isNotPresent();
		assertThat(request.count()).isNotPresent();
		assertThat(request.forward()).isTrue();
	}

	@Test
	void offsetBackwardPaginationNullSize() {
		OffsetScrollPosition position = OffsetScrollPosition.of(30);
		ScrollRequest request = new ScrollRequest(position, null, false);

		assertThat(((OffsetScrollPosition) request.position().get())).isEqualTo(position);
		assertThat(request.count()).isNotPresent();
		assertThat(request.forward()).isTrue();
	}

}

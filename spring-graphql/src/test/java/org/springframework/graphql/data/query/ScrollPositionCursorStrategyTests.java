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

package org.springframework.graphql.data.query;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.ScrollPosition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScrollPositionCursorStrategy}.
 *
 * @author Rossen Stoyanchev
 * @author Oliver Drotbohm
 */
class ScrollPositionCursorStrategyTests {

	private final ScrollPositionCursorStrategy cursorStrategy = new ScrollPositionCursorStrategy();


	@Test
	void offsetPosition() {
		toAndFromCursor(ScrollPosition.offset(43), "O_43");
	}

	@Test
	void keysetPosition() {
		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);

		toAndFromCursor(ScrollPosition.forward(keys),
				"K_[\"java.util.Collections$UnmodifiableMap\"," +
						"{\"firstName\":\"Joseph\",\"lastName\":\"Heller\",\"id\":103}]");
	}

	private void toAndFromCursor(ScrollPosition position, String cursor) {
		assertThat(this.cursorStrategy.toCursor(position)).isEqualTo(cursor);
		assertThat(this.cursorStrategy.fromCursor(cursor)).isEqualTo(position);
	}

}

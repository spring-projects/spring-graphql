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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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

	@ParameterizedTest
	@MethodSource("mapInstances")
	void fromJsonToCursor(Map<String, Object> keys) {
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);

		assertThat(this.cursorStrategy.fromCursor("K_[\"" + keys.getClass().getName() + "\"," +
				"{\"firstName\":\"Joseph\",\"lastName\":\"Heller\",\"id\":103}]"))
				.isEqualTo(ScrollPosition.forward(keys));
	}

	@ParameterizedTest
	@MethodSource("mapInstances")
	void fromCursorToJson(Map<String, Object> keys) {
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);
		KeysetScrollPosition position = ScrollPosition.forward(keys);

		assertThat(this.cursorStrategy.toCursor(position)).isEqualTo(
				"K_[\"java.util.Collections$UnmodifiableMap\"," +
						"{\"firstName\":\"Joseph\",\"lastName\":\"Heller\",\"id\":103}]");
	}

	@Test
	void fromJsonToCursorFailsForInvalidTypes() {
		assertThatIllegalArgumentException().isThrownBy(() ->
						this.cursorStrategy.fromCursor("K_[\"" + MultiValueMap.class.getName() + "\"," +
				"{\"firstName\":\"Joseph\",\"lastName\":\"Heller\",\"id\":103}]"));
	}

	static Stream<Arguments> mapInstances() {
		return Stream.of(
				Arguments.argumentSet("LinkedHashMap", new LinkedHashMap<String, Object>()),
				Arguments.argumentSet("HashMap", new HashMap<String, Object>())
		);
	}

	private void toAndFromCursor(ScrollPosition position, String cursor) {
		assertThat(this.cursorStrategy.toCursor(position)).isEqualTo(cursor);
		assertThat(this.cursorStrategy.fromCursor(cursor)).isEqualTo(position);
	}

}

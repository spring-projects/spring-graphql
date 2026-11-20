/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

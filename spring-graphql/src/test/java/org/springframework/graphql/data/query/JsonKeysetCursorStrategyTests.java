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

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonKeysetCursorStrategy}.
 *
 * @author Rossen Stoyanchev
 */
class JsonKeysetCursorStrategyTests {

	private final JsonKeysetCursorStrategy cursorStrategy = new JsonKeysetCursorStrategy();


	@Test
	void toAndFromCursor() {
		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("firstName", "Joseph");
		keys.put("lastName", "Heller");
		keys.put("id", 103);

		String json = "[\"java.util.LinkedHashMap\",{\"firstName\":\"Joseph\",\"lastName\":\"Heller\",\"id\":103}]";

		assertThat(this.cursorStrategy.toCursor(keys)).isEqualTo(json);
		assertThat(this.cursorStrategy.fromCursor(json)).isEqualTo(keys);
	}

	@Test
	void toAndFromCursorWithDate() {

		Date date = new Date();

		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("date", date);
		String json = "[\"java.util.LinkedHashMap\",{\"date\":[\"java.util.Date\"," + date.getTime() + "]}]";

		assertThat(this.cursorStrategy.toCursor(keys)).isEqualTo(json);
		assertThat(this.cursorStrategy.fromCursor(json)).isEqualTo(keys);
	}

	@Test
	void toAndFromCursorWithZonedDateTime() {

		ZonedDateTime dateTime = ZonedDateTime.of(
				LocalDateTime.of(2023, Month.MAY, 5, 0, 0, 0, 0), ZoneId.of("Z"));

		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("date", dateTime);
		String json = "[\"java.util.LinkedHashMap\",{\"date\":[\"java.time.ZonedDateTime\",1683244800.000000000]}]";

		assertThat(this.cursorStrategy.toCursor(keys)).isEqualTo(json);
		assertThat(this.cursorStrategy.fromCursor(json)).isEqualTo(keys);
	}

	@Test
	void toAndFromCursorWithUUID() {

		UUID uuid = UUID.randomUUID();

		Map<String, Object> keys = new LinkedHashMap<>();
		keys.put("uuid", uuid);
		String json = "[\"java.util.LinkedHashMap\",{\"uuid\":[\"java.util.UUID\",\"" + uuid + "\"]}]";

		assertThat(this.cursorStrategy.toCursor(keys)).isEqualTo(json);
		assertThat(this.cursorStrategy.fromCursor(json)).isEqualTo(keys);
	}
}

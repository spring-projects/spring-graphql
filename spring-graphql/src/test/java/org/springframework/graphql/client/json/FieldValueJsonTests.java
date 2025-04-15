/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.client.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.FieldValue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link org.springframework.graphql.FieldValue} support in {@link GraphQlModule}.
 */
class FieldValueJsonTests {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new GraphQlModule());

	@Nested
	class DeserializationTests {

		@Test
		void valueIsOmittedWhenJsonKeyMissing() throws Exception {
			Library library = objectMapper.readValue("{}", Library.class);

			assertThat(library.name()).isNotNull()
				.satisfies(name -> assertThat(name.isOmitted()).isTrue());
		}

		@Test
		void valueIsEmptyWhenJsonValueIsNull() throws Exception {
			Library library = objectMapper.readValue("{\"name\":null}", Library.class);

			assertThat(library.name()).isNotNull()
					.satisfies(name -> assertThat(name.isEmpty()).isTrue());
		}

		@Test
		void valueIsPresentWhenJsonValueIsNotNull() throws Exception {
			Library library = objectMapper.readValue("{\"name\":\"The Library\"}", Library.class);

			assertThat(library.name()).isNotNull()
					.satisfies(
							name -> assertThat(name.isPresent()).isTrue(),
							name -> assertThat(name.value()).isEqualTo("The Library")
					);
		}

	}

	@Nested
	class SerializationTests {

		@Test
		void skipJsonAttributeWhenValueOmitted() throws Exception {
			Library library = new Library(FieldValue.omitted());

			assertThat(objectMapper.writeValueAsString(library)).isEqualTo("{}");
		}

		@Test
		void nullJsonValueWhenValueIsNull() throws Exception {
			Library library = new Library(FieldValue.ofNullable(null));

			assertThat(objectMapper.writeValueAsString(library)).isEqualTo("{\"name\":null}");
		}

		@Test
		void emptyJsonValueWhenValueIsEmpty() throws Exception {
			Library library = new Library(FieldValue.ofNullable(""));

			assertThat(objectMapper.writeValueAsString(library)).isEqualTo("{\"name\":\"\"}");
		}

		@Test
		void jsonValueWhenValueIsPresent() throws Exception {
			Library library = new Library(FieldValue.ofNullable("The Library"));

			assertThat(objectMapper.writeValueAsString(library)).isEqualTo("{\"name\":\"The Library\"}");
		}

	}

	public record Library(FieldValue<String> name) {

	}

}

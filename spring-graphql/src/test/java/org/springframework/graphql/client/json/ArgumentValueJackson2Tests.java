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

package org.springframework.graphql.client.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.data.ArgumentValue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArgumentValue} support in {@link GraphQlJackson2Module}.
 */
class ArgumentValueJackson2Tests {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new GraphQlJackson2Module());

	@Nested
	class SerializationTests {

		@Test
		void skipJsonAttributeWhenValueOmitted() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.omitted());

			assertThat(objectMapper.writeValueAsString(libraryInput)).isEqualTo("{}");
		}

		@Test
		void nullJsonValueWhenValueIsNull() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.ofNullable(null));

			assertThat(objectMapper.writeValueAsString(libraryInput)).isEqualTo("{\"name\":null}");
		}

		@Test
		void emptyJsonValueWhenValueIsEmpty() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.ofNullable(""));

			assertThat(objectMapper.writeValueAsString(libraryInput)).isEqualTo("{\"name\":\"\"}");
		}

		@Test
		void jsonValueWhenValueIsPresent() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.ofNullable("The Library"));

			assertThat(objectMapper.writeValueAsString(libraryInput)).isEqualTo("{\"name\":\"The Library\"}");
		}

	}

	public record LibraryInput(ArgumentValue<String> name) {

	}

}

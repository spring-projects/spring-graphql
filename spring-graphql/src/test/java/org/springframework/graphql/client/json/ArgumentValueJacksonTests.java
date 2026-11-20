/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client.json;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.graphql.data.ArgumentValue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArgumentValue} support in {@link GraphQlJacksonModule}.
 */
class ArgumentValueJacksonTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().addModule(new GraphQlJacksonModule()).build();

	@Nested
	class SerializationTests {

		@Test
		void skipJsonAttributeWhenValueOmitted() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.omitted());

			assertThat(jsonMapper.writeValueAsString(libraryInput)).isEqualTo("{}");
		}

		@Test
		void nullJsonValueWhenValueIsNull() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.ofNullable(null));

			assertThat(jsonMapper.writeValueAsString(libraryInput)).isEqualTo("{\"name\":null}");
		}

		@Test
		void emptyJsonValueWhenValueIsEmpty() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.ofNullable(""));

			assertThat(jsonMapper.writeValueAsString(libraryInput)).isEqualTo("{\"name\":\"\"}");
		}

		@Test
		void jsonValueWhenValueIsPresent() throws Exception {
			LibraryInput libraryInput = new LibraryInput(ArgumentValue.ofNullable("The Library"));

			assertThat(jsonMapper.writeValueAsString(libraryInput)).isEqualTo("{\"name\":\"The Library\"}");
		}

	}

	public record LibraryInput(ArgumentValue<String> name) {

	}

}

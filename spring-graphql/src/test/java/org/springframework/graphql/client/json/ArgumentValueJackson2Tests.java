/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

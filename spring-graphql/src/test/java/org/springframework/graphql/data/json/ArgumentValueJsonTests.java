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

package org.springframework.graphql.data.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.data.ArgumentValue;

import static org.assertj.core.api.Assertions.assertThat;

public class ArgumentValueJsonTests {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().registerModule(new GraphQLModule());
	}

	@Test
	void readValue_ValueIsOmitted_ReturnOmitted() throws Exception {
		final var json = "{}";
		final var library = objectMapper.readValue(json, Library.class);

		assertThat(library.name())
			.isNotNull()
			.satisfies(
				name -> assertThat(name.isOmitted()).isTrue()
			);
	}

	@Test
	void readValue_ValueIsEmpty_ReturnEmpty() throws Exception {
		final var json = "{\"name\":null}";
		final var library = objectMapper.readValue(json, Library.class);

		assertThat(library.name())
			.isNotNull()
			.satisfies(
				name -> assertThat(name.isOmitted()).isFalse(),
				name -> assertThat(name.value()).isNull()
			);
	}

	@Test
	void readValue_ValueIsPresent_ReturnPresent() throws Exception {
		final var json = "{\"name\":\"The Library\"}";
		final var library = objectMapper.readValue(json, Library.class);

		assertThat(library.name())
			.isNotNull()
			.satisfies(
				name -> assertThat(name.isPresent()).isTrue(),
				name -> assertThat(name.value()).isEqualTo("The Library")
			);
	}

	@Test
	void writeValueAsString_ValueIsOmitted_ReturnOmitted() throws Exception {
		final var library = new Library(ArgumentValue.omitted());
		final var json = objectMapper.writeValueAsString(library);

		assertThat(json).isEqualTo("{}");
	}

	@Test
	void writeValueAsString_ValueIsEmpty_ReturnEmpty() throws Exception {
		final var library = new Library(ArgumentValue.ofNullable(null));
		final var json = objectMapper.writeValueAsString(library);

		assertThat(json).isEqualTo("{\"name\":null}");
	}

	@Test
	void writeValueAsString_ValueIsEmptyString_ReturnEmptyString() throws Exception {
		final var library = new Library(ArgumentValue.ofNullable(""));
		final var json = objectMapper.writeValueAsString(library);

		assertThat(json).isEqualTo("{\"name\":\"\"}");
	}

	@Test
	void writeValueAsString_ValueIsPresent_ReturnPresent() throws Exception {
		final var library = new Library(ArgumentValue.ofNullable("The Library"));
		final var json = objectMapper.writeValueAsString(library);

		assertThat(json).isEqualTo("{\"name\":\"The Library\"}");
	}

	public record Library(ArgumentValue<String> name) {}

}

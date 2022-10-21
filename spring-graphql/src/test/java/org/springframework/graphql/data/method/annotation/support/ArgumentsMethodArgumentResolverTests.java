/*
 * Copyright 2020-2022 the original author or authors.
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

package org.springframework.graphql.data.method.annotation.support;


import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Arguments;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArgumentsMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
class ArgumentsMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final HandlerMethodArgumentResolver resolver = new ArgumentsMethodArgumentResolver(
			new GraphQlArgumentBinder(new DefaultFormattingConversionService()));


	@Test
	void shouldSupportAnnotatedParameters() {
		MethodParameter methodParameter = methodParam(BookController.class, "addBook", BookInput.class);
		assertThat(resolver.supportsParameter(methodParameter)).isTrue();
	}

	@Test
	void shouldNotSupportParametersWithoutAnnotation() {
		MethodParameter methodParameter = methodParam(BookController.class, "notSupported", String.class);
		assertThat(resolver.supportsParameter(methodParameter)).isFalse();
	}

	@Test
	void shouldResolveJavaBeanArgument() throws Exception {
		Object result = resolver.resolveArgument(
				methodParam(BookController.class, "addBook", BookInput.class),
				environment("{\"name\":\"test name\", \"authorId\":42}"));

		assertThat(result)
				.isNotNull()
				.isInstanceOf(BookInput.class)
				.satisfies(value -> {
					BookInput input = (BookInput) value;
					assertThat(input.getName().isPresent()).isTrue();
					assertThat(input.getName().isOmitted()).isFalse();
					assertThat(input.getName().value()).isEqualTo("test name");
					assertThat(input.getAuthorId()).isEqualTo(42L);
				});
	}


	@SuppressWarnings({"ConstantConditions", "unused"})
	@Controller
	static class BookController {

		public void notSupported(String param) {
		}

		@MutationMapping
		public Book addBook(@Arguments BookInput bookInput) {
			return null;
		}

	}

	@SuppressWarnings({"NotNullFieldNotInitialized", "unused"})
	static class BookInput {

		ArgumentValue<String> name;

		Long authorId;

		public ArgumentValue<String> getName() {
			return this.name;
		}

		public void setName(ArgumentValue<String> name) {
			this.name = name;
		}

		public Long getAuthorId() {
			return this.authorId;
		}

		public void setAuthorId(Long authorId) {
			this.authorId = authorId;
		}
	}

}
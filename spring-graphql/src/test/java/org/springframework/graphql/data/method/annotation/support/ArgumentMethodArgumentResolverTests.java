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


import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArgumentMethodArgumentResolver}.
 * @author Brian Clozel
 */
class ArgumentMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final HandlerMethodArgumentResolver resolver = new ArgumentMethodArgumentResolver(
			new GraphQlArgumentBinder(new DefaultFormattingConversionService()));


	@Test
	void supportsParameter() {
		MethodParameter param = methodParam(BookController.class, "bookById", Long.class);
		assertThat(this.resolver.supportsParameter(param)).isTrue();

		param = methodParam(BookController.class, "addBook", ArgumentValue.class);
		assertThat(this.resolver.supportsParameter(param)).isTrue();

		param = methodParam(BookController.class, "notSupported", String.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();
	}

	@Test
	void shouldResolveBasicTypeArgument() throws Exception {
		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "bookById", Long.class),
				environment("{\"id\": 42 }"));

		assertThat(result).isNotNull().isInstanceOf(Long.class).isEqualTo(42L);
	}

	@Test
	void shouldResolveJavaBeanArgument() throws Exception {
		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "addBook", BookInput.class),
				environment("{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42} }"));

		assertThat(result).isNotNull().isInstanceOf(BookInput.class)
				.hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L);
	}

	@Test
	void shouldResolveJavaBeanArgumentWithWrapper() throws Exception {
		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "addBook", ArgumentValue.class),
				environment("{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42} }"));

		assertThat(result)
				.isNotNull()
				.isInstanceOf(ArgumentValue.class)
				.extracting(value -> ((ArgumentValue<?>) value).value())
				.hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L);
	}

	@Test
	void shouldResolveListOfJavaBeansArgument() throws Exception {
		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "addBooks", List.class),
				environment("{\"books\": [" +
						"{ \"name\": \"first\", \"authorId\": 42}, " +
						"{ \"name\": \"second\", \"authorId\": 24}] }"));

		assertThat(result).isNotNull()
				.isInstanceOf(List.class).asList()
				.allMatch(item -> item instanceof Book)
				.extracting("name").containsExactly("first", "second");
	}

	@Test
	void shouldResolveArgumentWithConversionService() throws Exception {
		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "bookByKeyword", Keyword.class),
				environment("{\"keyword\": \"test\" }"));

		assertThat(result).isNotNull().isInstanceOf(Keyword.class).hasFieldOrPropertyWithValue("term", "test");
	}


	@SuppressWarnings({"ConstantConditions", "unused"})
	@Controller
	static class BookController {

		public void notSupported(String param) {

		}

		@QueryMapping
		public Book bookById(@Argument Long id) {
			return null;
		}

		@MutationMapping
		public Book addBook(@Argument BookInput bookInput) {
			return null;
		}

		@MutationMapping
		public Book addBook(ArgumentValue<BookInput> bookInput) {
			return null;
		}

		@MutationMapping
		public List<Book> addBooks(@Argument List<Book> books) {
			return null;
		}

		@QueryMapping
		public List<Book> bookByKeyword(@Argument Keyword keyword) {
			return null;
		}

	}

	@SuppressWarnings({"NotNullFieldNotInitialized", "unused"})
	static class BookInput {

		String name;

		Long authorId;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Long getAuthorId() {
			return this.authorId;
		}

		public void setAuthorId(Long authorId) {
			this.authorId = authorId;
		}
	}


	@SuppressWarnings("unused")
	static class Keyword {

		String term;

		private Keyword(String term) {
			this.term = term;
		}

		public static Keyword of(String term) {
			return new Keyword(term);
		}

		public String getTerm() {
			return this.term;
		}

		public void setTerm(String term) {
			this.term = term;
		}
	}

}
/*
 * Copyright 2020-2021 the original author or authors.
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


import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.OptionalInput;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArgumentMethodArgumentResolver}.
 *
 * @author Brian Clozel
 */
class ArgumentMethodArgumentResolverTests {

	private final ObjectMapper mapper = new ObjectMapper();

	ArgumentMethodArgumentResolver resolver = new ArgumentMethodArgumentResolver();

	@Test
	void shouldSupportAnnotatedParameters() {
		Method bookById = ClassUtils.getMethod(BookController.class, "bookById", Long.class);
		MethodParameter methodParameter = getMethodParameter(bookById, 0);
		assertThat(resolver.supportsParameter(methodParameter)).isTrue();
	}

	@Test
	void shouldNotSupportParametersWithoutAnnotation() {
		Method notSupported = ClassUtils.getMethod(BookController.class, "notSupported", String.class);
		MethodParameter methodParameter = getMethodParameter(notSupported, 0);
		assertThat(resolver.supportsParameter(methodParameter)).isFalse();
	}

	@Test
	void shouldResolveBasicTypeArgument() throws Exception {
		Method bookById = ClassUtils.getMethod(BookController.class, "bookById", Long.class);
		String payload = "{\"id\": 42 }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(bookById, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(Long.class).isEqualTo(42L);
	}

	@Test
	void shouldResolveJavaBeanArgument() throws Exception {
		Method addBook = ClassUtils.getMethod(BookController.class, "addBook", BookInput.class);
		String payload = "{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(addBook, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(BookInput.class);
		assertThat((BookInput) result).hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L)
				.hasFieldOrPropertyWithValue("notes", OptionalInput.undefined());
	}

	@Test
	void shouldResolveJavaBeanOptionalArgument() throws Exception {
		Method addBook = ClassUtils.getMethod(BookController.class, "addBook", BookInput.class);
		String payload = "{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42, \"notes\": \"Hello\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(addBook, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(BookInput.class);
		assertThat((BookInput) result)
				.hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L)
				.hasFieldOrPropertyWithValue("notes", OptionalInput.defined("Hello"));
	}

	@Test
	void shouldResolveJavaBeanOptionalNullArgument() throws Exception {
		Method addBook = ClassUtils.getMethod(BookController.class, "addBook", BookInput.class);
		String payload = "{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42, \"notes\": null} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(addBook, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(BookInput.class);
		assertThat((BookInput) result)
				.hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L)
				.hasFieldOrPropertyWithValue("notes", OptionalInput.defined(null));
	}

	@Test
	void shouldResolveKotlinBeanArgument() throws Exception {
		Method addBook = ClassUtils.getMethod(BookController.class, "ktAddBook", KotlinBookInput.class);
		String payload = "{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(addBook, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(KotlinBookInput.class);
		assertThat((KotlinBookInput) result)
				.hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L)
				.hasFieldOrPropertyWithValue("notes", OptionalInput.undefined());
	}

	@Test
	void shouldResolveKotlinBeanOptionalArgument() throws Exception {
		Method addBook = ClassUtils.getMethod(BookController.class, "ktAddBook", KotlinBookInput.class);
		String payload = "{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42, \"notes\": \"Hello\"} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(addBook, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(KotlinBookInput.class);
		assertThat((KotlinBookInput) result)
				.hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L)
				.hasFieldOrPropertyWithValue("notes", OptionalInput.defined("Hello"));
	}

	@Test
	void shouldResolveKotlinBeanOptionalNullArgument() throws Exception {
		Method addBook = ClassUtils.getMethod(BookController.class, "ktAddBook", KotlinBookInput.class);
		String payload = "{\"bookInput\": { \"name\": \"test name\", \"authorId\": 42, \"notes\": null} }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(addBook, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(KotlinBookInput.class);
		assertThat((KotlinBookInput) result)
				.hasFieldOrPropertyWithValue("name", "test name")
				.hasFieldOrPropertyWithValue("authorId", 42L)
				.hasFieldOrPropertyWithValue("notes", OptionalInput.defined(null));
	}

	@Test
	void shouldResolveListOfJavaBeansArgument() throws Exception {
		Method addBooks = ClassUtils.getMethod(BookController.class, "addBooks", List.class);
		String payload = "{\"books\": [{ \"name\": \"first\", \"authorId\": 42}, { \"name\": \"second\", \"authorId\": 24}] }";
		DataFetchingEnvironment environment = initEnvironment(payload);
		MethodParameter methodParameter = getMethodParameter(addBooks, 0);
		Object result = resolver.resolveArgument(methodParameter, environment);
		assertThat(result).isNotNull().isInstanceOf(List.class);
		assertThat(result).asList().allMatch(item -> item instanceof Book)
				.extracting("name").containsExactly("first", "second");
	}

	private MethodParameter getMethodParameter(Method method, int index) {
		MethodParameter methodParameter = new MethodParameter(method, index);
		methodParameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
		return methodParameter;
	}

	private DataFetchingEnvironment initEnvironment(String jsonPayload) throws JsonProcessingException {
		Map<String, Object> arguments = mapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {
		});
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();
	}

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
		public Book ktAddBook(@Argument KotlinBookInput bookInput) {
			return null;
		}

		@MutationMapping
		public List<Book> addBooks(@Argument List<Book> books) {
			return null;
		}

	}

	static class BookInput {

		String name;

		Long authorId;

		OptionalInput<String> notes = OptionalInput.undefined();

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

		public OptionalInput<String> getNotes() {
			return this.notes;
		}

		public void setNotes(OptionalInput<String> notes) {
			this.notes = (notes == null) ? OptionalInput.defined(null) : notes;
		}
	}

	static class Keyword {

		String term;

		public String getTerm() {
			return this.term;
		}

		public void setTerm(String term) {
			this.term = term;
		}
	}

}
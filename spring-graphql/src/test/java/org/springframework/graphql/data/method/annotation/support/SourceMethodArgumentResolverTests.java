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

package org.springframework.graphql.data.method.annotation.support;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SourceMethodArgumentResolver}.
 * @author Brian Clozel
 */
class SourceMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final HandlerMethodArgumentResolver resolver = new SourceMethodArgumentResolver();

	@ParameterizedTest
	@MethodSource("excludedTypes")
	void excludedSourceTypes(Class<?> argumentType) {
		MethodParameter param = methodParam(BookController.class, "notSupported", argumentType);
		assertThat(this.resolver.supportsParameter(param)).isFalse();
	}

	static Stream<Class<?>> excludedTypes() {
		return Stream.of(List.class, String[].class, Integer.class, Date.class, Instant.class, URI.class, URL.class, Locale.class, Class.class);
	}

	@Test
	void supportedSourceTypes() {
		MethodParameter param = methodParam(BookController.class, "author", Book.class);
		assertThat(this.resolver.supportsParameter(param)).isTrue();

		param = methodParam(BookController.class, "name", Format.class);
		assertThat(this.resolver.supportsParameter(param)).isTrue();
	}

	@Test
	void bindArgumentWhenPresent() throws Exception {
		Book aBook = BookSource.getBook(1L);
		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "author", Book.class),
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(aBook).build());

		assertThat(result).isNotNull().isInstanceOf(Book.class).isEqualTo(aBook);
	}

	@Test
	void bindArgumentWhenUnavailable() throws Exception {
		MethodParameter methodParameter = methodParam(BookController.class, "author", Book.class);
		assertThatThrownBy(() -> this.resolver.resolveArgument(
				methodParameter,
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Parameter [0] in %s: was not recognized by any resolver and there is no source/parent either.", methodParameter.getMethod());
	}

	@Test
	void bindArgumentWhenWrongType() throws Exception {
		MethodParameter methodParameter = methodParam(BookController.class, "author", Book.class);
		assertThatThrownBy(() -> this.resolver.resolveArgument(
				methodParameter,
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(2L).build())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Parameter [0] in %s: does not match the source Object type 'class java.lang.Long'.", methodParameter.getMethod());
	}


	@SuppressWarnings({"ConstantConditions", "unused"})
	@Controller
	static class BookController {

		public void notSupported(Integer integer) {

		}

		public void notSupported(Date date) {

		}

		public void notSupported(Instant instant) {

		}

		public void notSupported(URI uri) {

		}

		public void notSupported(URL url) {

		}

		public void notSupported(Locale locale) {

		}

		public void notSupported(Class<?> klass) {

		}

		public void notSupported(List<String> list) {

		}

		public void notSupported(String[] array) {

		}

		@SchemaMapping
		public Author author(Book book) {
			return null;
		}

		@SchemaMapping
		public String name(Format format) {
			return format.name();
		}

	}

	enum Format {
		PAPERBACK, EBOOK
	}
}

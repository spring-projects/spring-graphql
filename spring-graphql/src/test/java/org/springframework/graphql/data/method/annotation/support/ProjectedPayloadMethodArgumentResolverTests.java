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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProjectedPayloadMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
public class ProjectedPayloadMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private ProjectedPayloadMethodArgumentResolver resolver;


	@BeforeEach
	void setUp() {
		StaticApplicationContext context = new StaticApplicationContext();
		this.resolver = new ProjectedPayloadMethodArgumentResolver(context);
	}


	@Test
	void supports() {
		testSupports("projection", BookProjection.class, true);
		testSupports("optionalProjection", Optional.class, true);
		testSupports("optionalString", Optional.class, false);
		testSupports("argumentValueProjection", ArgumentValue.class, true);
	}

	void testSupports(String methodName, Class<?> methodParamType, boolean supported) {
		MethodParameter param = methodParam(BookController.class, methodName, methodParamType);
		assertThat(this.resolver.supportsParameter(param)).isEqualTo(supported);
	}

	@Test
	void optionalPresent() throws Exception {

		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "optionalProjection", Optional.class),
				environment("{ \"where\" : { \"author\" : \"Orwell\" }}"));

		assertThat(result).isNotNull().isInstanceOf(Optional.class);
		BookProjection book = ((Optional<BookProjection>) result).get();
		assertThat(book.getAuthor()).isEqualTo("Orwell");
	}

	@Test
	void optionalNotPresent() throws Exception {

		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "optionalProjection", Optional.class),
				environment("{}"));

		assertThat(result).isNotNull().isInstanceOf(Optional.class);
		assertThat((Optional<?>) result).isNotPresent();
	}

	@Test
	void argumentValuePresent() throws Exception {

		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "argumentValueProjection", ArgumentValue.class),
				environment("{ \"where\" : { \"author\" : \"Orwell\" }}"));

		assertThat(result).isNotNull().isInstanceOf(ArgumentValue.class);
		BookProjection book = ((ArgumentValue<BookProjection>) result).value();
		assertThat(book.getAuthor()).isEqualTo("Orwell");
	}

	@Test
	void argumentValueSetToNull() throws Exception {

		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "argumentValueProjection", ArgumentValue.class),
				environment("{ \"where\" : null}"));

		assertThat(result).isNotNull().isInstanceOf(ArgumentValue.class);
		ArgumentValue<BookProjection> value = (ArgumentValue<BookProjection>) result;
		assertThat(value.isPresent()).isFalse();
		assertThat(value.isOmitted()).isFalse();
	}

	@Test
	void argumentValueIsOmitted() throws Exception {

		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "argumentValueProjection", ArgumentValue.class),
				environment("{}"));

		assertThat(result).isNotNull().isInstanceOf(ArgumentValue.class);
		ArgumentValue<BookProjection> value = (ArgumentValue<BookProjection>) result;
		assertThat(value.isPresent()).isFalse();
		assertThat(value.isOmitted()).isTrue();
	}

	@Test // gh-550
	void nullValue() throws Exception {

		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "projection", BookProjection.class),
				environment("{}"));

		assertThat(result).isNull();
	}


	@SuppressWarnings({"ConstantConditions", "unused"})
	@Controller
	static class BookController {

		@QueryMapping
		public List<Book> projection(@Argument(name = "where") BookProjection projection) {
			return null;
		}

		@QueryMapping
		public List<Book> optionalProjection(@Argument(name = "where") Optional<BookProjection> projection) {
			return null;
		}

		@QueryMapping
		public void optionalString(@Argument Optional<String> projection) {
		}

		@QueryMapping
		public List<Book> argumentValueProjection(@Argument(name = "where") ArgumentValue<BookProjection> projection) {
			return null;
		}

	}


	@ProjectedPayload
	interface BookProjection {

		String getAuthor();

	}

}

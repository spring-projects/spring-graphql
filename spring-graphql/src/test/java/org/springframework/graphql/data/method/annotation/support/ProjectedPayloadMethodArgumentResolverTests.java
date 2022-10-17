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
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
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
		MethodParameter param = methodParam(BookController.class, "optionalProjection", Optional.class);
		assertThat(this.resolver.supportsParameter(param)).isTrue();

		param = methodParam(BookController.class, "optionalString", Optional.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();
	}

	@Test
	void optionalWrapper() throws Exception {

		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "optionalProjection", Optional.class),
				environment("{}"));

		assertThat(result).isNotNull().isInstanceOf(Optional.class);
		assertThat((Optional<?>) result).isNotPresent();
	}


	@SuppressWarnings({"ConstantConditions", "unused"})
	@Controller
	static class BookController {

		@QueryMapping
		public List<Book> optionalProjection(@Argument(name = "where") Optional<BookProjection> projection) {
			return null;
		}

		@QueryMapping
		public void optionalString(@Argument Optional<String> projection) {
		}

	}


	@ProjectedPayload
	interface BookProjection {

		String getAuthor();

	}

}

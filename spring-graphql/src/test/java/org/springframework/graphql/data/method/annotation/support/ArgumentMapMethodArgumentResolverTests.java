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


import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.Arguments;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArgumentMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
class ArgumentMapMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final HandlerMethodArgumentResolver resolver = new ArgumentMapMethodArgumentResolver();


	@Test
	void shouldSupportAnnotatedParameters() {
		MethodParameter param = methodParam(BookController.class, "argumentMap", Map.class);
		assertThat(this.resolver.supportsParameter(param)).isTrue();

		param = methodParam(BookController.class, "argumentsMap", Map.class);
		assertThat(this.resolver.supportsParameter(param)).isTrue();

		param = methodParam(BookController.class, "argument", Long.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();

		param = methodParam(BookController.class, "namedArgumentMap", Map.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();

		param = methodParam(BookController.class, "notAnnotated", String.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();
	}

	@Test
	void shouldResolveRawArgumentsMap() throws Exception {
		Object result = this.resolver.resolveArgument(
				methodParam(BookController.class, "argumentMap", Map.class),
				environment("{\"id\": 42 }"));

		assertThat(result).isNotNull().isInstanceOf(Map.class).isEqualTo(Collections.singletonMap("id", 42));
	}


	@SuppressWarnings({"ConstantConditions", "unused"})
	@Controller
	static class BookController {

		@QueryMapping
		public Book argumentMap(@Argument Map<?, ?> args) {
			return null;
		}

		@QueryMapping
		public Book argumentsMap(@Arguments Map<?, ?> args) {
			return null;
		}

		@QueryMapping
		public Book argument(@Argument Long id) {
			return null;
		}

		@QueryMapping
		public Book namedArgumentMap(@Argument(name = "book") Map<?, ?> book) {
			return null;
		}

		public void notAnnotated(String param) {
		}

	}

}
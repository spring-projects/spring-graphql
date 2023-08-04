/*
 * Copyright 2002-2023 the original author or authors.
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
import java.util.function.Function;
import java.util.stream.Collectors;

import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookCriteria;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.query.AbstractSortStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SortMethodArgumentResolver}.
 *
 * @author Rossen Stoyanchev
 */
public class SortMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final SortMethodArgumentResolver resolver = new SortMethodArgumentResolver(new MySortStrategy());

	private final MethodParameter param = methodParam(BookController.class, "getBooks", Sort.class);

	private final MethodParameter paramOptional = methodParam(BookController.class, "getBooksOptional", Optional.class);


	@Test
	void supports() {
		assertThat(this.resolver.supportsParameter(this.param)).isTrue();
		assertThat(this.resolver.supportsParameter(this.paramOptional)).isTrue();

		MethodParameter param = methodParam(BookController.class, "getBooksByCriteria", BookCriteria.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();
	}

	@SuppressWarnings({"unchecked", "DataFlowIssue", "OptionalGetWithoutIsPresent"})
	@Test
	void resolve() throws Exception {
		testResolver(env -> (Sort) this.resolver.resolveArgument(this.param, env));
		testResolver(env -> ((Optional<Sort>) this.resolver.resolveArgument(this.paramOptional, env)).get());
	}

	private void testResolver(Function<DataFetchingEnvironment, Sort> resolveFunction) throws Exception {
		DataFetchingEnvironment environment = environment("""
			{ "sortFields": ["firstName", "lastName", "id"], "sortDirection": "DESC"}"
		""");

		Sort sort = resolveFunction.apply(environment);

		assertThat(sort.stream().collect(Collectors.toList()))
				.hasSize(3)
				.containsExactly(
						new Sort.Order(Sort.Direction.DESC, "firstName"),
						new Sort.Order(Sort.Direction.DESC, "lastName"),
						new Sort.Order(Sort.Direction.DESC, "id"));
	}


	@SuppressWarnings({"unused", "DataFlowIssue", "OptionalUsedAsFieldOrParameterType"})
	private static class BookController {

		@QueryMapping
		public List<Book> getBooks(Sort sort) {
			return null;
		}

		@QueryMapping
		public List<Book> getBooksOptional(Optional<Sort> sort) {
			return null;
		}

		@QueryMapping
		public List<Book> getBooksByCriteria(BookCriteria criteria) {
			return null;
		}

	}


	private static class MySortStrategy extends AbstractSortStrategy {

		@Override
		protected List<String> getProperties(DataFetchingEnvironment env) {
			return env.getArgument("sortFields");
		}

		@Override
		protected Sort.Direction getDirection(DataFetchingEnvironment env) {
			String direction = env.getArgument("sortDirection");
			return direction != null ? Sort.Direction.valueOf(direction) : null;
		}

	}

}

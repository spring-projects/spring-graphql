/*
 * Copyright 2020-2023 the original author or authors.
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

import java.util.Map;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Window;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.pagination.Subrange;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SubrangeMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
public class SubrangeMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final SubrangeMethodArgumentResolver<MyPosition> resolver =
			new SubrangeMethodArgumentResolver<>(new MyPositionCursorStrategy());

	private final MethodParameter param =
			methodParam(BookController.class, "getBooks", Subrange.class);


	@Test
	void supports() {
		assertThat(this.resolver.supportsParameter(this.param)).isTrue();

		MethodParameter param = methodParam(BookController.class, "getBooksWithUnknownPosition", Subrange.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();
	}

	@Test
	void forwardPagination() throws Exception {
		int count = 10;
		int index = 25;
		Map<String, Object> arguments = Map.of("first", count, "after", String.valueOf(index));
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		testRequest(count, index, result, true);
	}

	@Test
	void backwardPagination() throws Exception {
		int count = 20;
		int index = 100;
		Map<String, Object> arguments = Map.of("last", count, "before", String.valueOf(index));
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		testRequest(count, index, result, false);
	}

	private static void testRequest(int count, int index, Object result, boolean forward) {
		Subrange<MyPosition> subrange = (Subrange<MyPosition>) result;
		assertThat(subrange.position().get().index()).isEqualTo(index);
		assertThat(subrange.count().orElse(0)).isEqualTo(count);
		assertThat(subrange.forward()).isEqualTo(forward);
	}


	@SuppressWarnings({"unused", "DataFlowIssue"})
	@Controller
	private static class BookController {

		@QueryMapping
		public Window<Book> getBooks(Subrange<MyPosition> subrange) {
			return null;
		}

		@QueryMapping
		public Window<Book> getBooksWithUnknownPosition(Subrange<UnknownPosition> subrange) {
			return null;
		}

	}


	private static class MyPositionCursorStrategy implements CursorStrategy<MyPosition> {


		@Override
		public boolean supports(Class<?> targetType) {
			return targetType.equals(MyPosition.class);
		}

		@Override
		public String toCursor(MyPosition position) {
			return String.valueOf(position.index());
		}

		@Override
		public MyPosition fromCursor(String cursor) {
			return new MyPosition(Integer.parseInt(cursor));
		}

	}


	private record MyPosition(int index) {
	}


	private static class UnknownPosition {
	}

}

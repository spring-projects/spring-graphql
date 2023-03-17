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
import org.springframework.graphql.data.pagination.PaginationRequest;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaginationRequestMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
public class PaginationRequestMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final PaginationRequestMethodArgumentResolver<MyPosition> resolver =
			new PaginationRequestMethodArgumentResolver<>(new MyPositionCursorStrategy());

	private final MethodParameter param =
			methodParam(BookController.class, "getBooks", PaginationRequest.class);


	@Test
	void supports() {
		assertThat(this.resolver.supportsParameter(this.param)).isTrue();

		MethodParameter param = methodParam(BookController.class, "getBooksWithUnknownPosition", PaginationRequest.class);
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
		PaginationRequest<MyPosition> request = (PaginationRequest<MyPosition>) result;
		assertThat(request.position().get().index()).isEqualTo(index);
		assertThat(request.count().get()).isEqualTo(count);
		assertThat(request.forward()).isEqualTo(forward);
	}

	private static DataFetchingEnvironment environment(Map<String, Object> arguments) {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BookController {

		@QueryMapping
		public Window<Book> getBooks(PaginationRequest<MyPosition> request) {
			return null;
		}

		@QueryMapping
		public Window<Book> getBooksWithUnknownPosition(PaginationRequest<UnknownPosition> request) {
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

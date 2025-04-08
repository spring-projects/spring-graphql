/*
 * Copyright 2020-2024 the original author or authors.
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
import org.springframework.data.domain.Window;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.pagination.Subrange;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SubrangeMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
class SubrangeMethodArgumentResolverTests extends ArgumentResolverTestSupport {

	private final SubrangeMethodArgumentResolver<MyPosition> resolver =
			new SubrangeMethodArgumentResolver<>(new MyPositionCursorStrategy());

	private final MethodParameter param = methodParam(BookController.class, "getBooks", Subrange.class);


	@Test
	void supports() {
		assertThat(this.resolver.supportsParameter(this.param)).isTrue();

		MethodParameter param = methodParam(BookController.class, "getBooksWithUnknownPosition", Subrange.class);
		assertThat(this.resolver.supportsParameter(param)).isFalse();
	}

	@Test
	void forward() throws Exception {
		int count = 10;
		int index = 25;
		Map<String, Object> arguments = Map.of("first", count, "after", String.valueOf(index));
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		assertResult(true, count, index, result);
	}

	@Test
	void forwardWithCountOnly() throws Exception {
		int count = 10;
		Map<String, Object> arguments = Map.of("first", count);
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		assertResult(true, count, null, result);
	}

	@Test
	void forwardWithIndexOnly() throws Exception {
		int index = 25;
		Map<String, Object> arguments = Map.of("after", String.valueOf(index));
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		assertResult(true, null, index, result);
	}

	@Test
	void backward() throws Exception {
		int count = 20;
		int index = 100;
		Map<String, Object> arguments = Map.of("last", count, "before", String.valueOf(index));
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		assertResult(false, count, index, result);
	}

	@Test
	void backwardWithCountOnly() throws Exception {
		int count = 10;
		Map<String, Object> arguments = Map.of("last", count);
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		assertResult(false, count, null, result);
	}

	@Test
	void backwardWithIndexOnly() throws Exception {
		int index = 25;
		Map<String, Object> arguments = Map.of("before", String.valueOf(index));
		Object result = this.resolver.resolveArgument(this.param, environment(arguments));

		assertResult(false, null, index, result);
	}

	@Test
	void noInput() throws Exception {
		Object result = this.resolver.resolveArgument(this.param, environment(Collections.emptyMap()));
		assertResult(true, null, null, result);
	}

	private static void assertResult(
			boolean forward, @Nullable Integer count, @Nullable Integer index, @Nullable Object result) {

		assertThat(result).isNotNull();
		Subrange<MyPosition> subrange = (Subrange<MyPosition>) result;
		assertThat(subrange.forward()).isEqualTo(forward);

		if (count != null) {
			assertThat(subrange.count().orElse(0)).isEqualTo(count);
		}
		else {
			assertThat(subrange.count()).isNotPresent();
		}

		if (index != null) {
			assertThat(subrange.position().get().index()).isEqualTo(index);
		}
		else {
			assertThat(subrange.position()).isNotPresent();
		}
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

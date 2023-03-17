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

package org.springframework.graphql.data.query;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WindowConnectionAdapter}.
 *
 * @author Rossen Stoyanchev
 */
public class WindowConnectionAdapterTests {

	private final WindowConnectionAdapter adapter = new WindowConnectionAdapter(new ScrollPositionCursorStrategy());


	@Test
	void paged() {
		List<Book> books = BookSource.books();
		Window<Book> window = Window.from(books, offset -> OffsetScrollPosition.of(35 + offset), true);

		assertThat(this.adapter.getContent(window)).isEqualTo(books);
		assertThat(this.adapter.hasNext(window)).isTrue();
		assertThat(this.adapter.hasPrevious(window)).isTrue();
		assertThat(this.adapter.cursorAt(window, 3)).isEqualTo("O_38");
	}

	@Test
	void unpaged() {
		List<Book> books = BookSource.books();
		Window<Book> window = Window.from(books, OffsetScrollPosition::of);

		assertThat(this.adapter.getContent(window)).isEqualTo(books);
		assertThat(this.adapter.hasNext(window)).isFalse();
		assertThat(this.adapter.hasPrevious(window)).isFalse();
		assertThat(this.adapter.cursorAt(window, 3)).isEqualTo("O_3");
	}

}

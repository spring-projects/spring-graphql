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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SliceConnectionAdapter}.
 *
 * @author Rossen Stoyanchev
 */
public class SliceConnectionAdapterTests {

	private final SliceConnectionAdapter adapter = new SliceConnectionAdapter(new ScrollPositionCursorStrategy());


	@Test
	void paged() {
		List<Book> books = BookSource.books();
		Page<Book> page = new PageImpl<>(books, PageRequest.of(5, books.size()), 50);

		assertThat(this.adapter.getContent(page)).isEqualTo(books);
		assertThat(this.adapter.hasNext(page)).isTrue();
		assertThat(this.adapter.hasPrevious(page)).isTrue();
		assertThat(this.adapter.cursorAt(page, 3)).isEqualTo("O_38");
	}

	@Test
	void unpaged() {
		List<Book> books = BookSource.books();
		Page<Book> page = new PageImpl<>(books);

		assertThat(this.adapter.getContent(page)).isEqualTo(books);
		assertThat(this.adapter.hasNext(page)).isFalse();
		assertThat(this.adapter.hasPrevious(page)).isFalse();
		assertThat(this.adapter.cursorAt(page, 3)).isEqualTo("O_3");
	}

}

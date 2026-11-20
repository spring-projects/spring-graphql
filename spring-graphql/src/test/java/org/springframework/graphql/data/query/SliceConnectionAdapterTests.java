/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
class SliceConnectionAdapterTests {

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

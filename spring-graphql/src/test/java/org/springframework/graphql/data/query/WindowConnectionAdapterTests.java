/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WindowConnectionAdapter}.
 *
 * @author Rossen Stoyanchev
 * @author Oliver Drotbohm
 */
class WindowConnectionAdapterTests {

	private final WindowConnectionAdapter adapter = new WindowConnectionAdapter(new ScrollPositionCursorStrategy());


	@Test
	void paged() {
		List<Book> books = BookSource.books();
		Window<Book> window = Window.from(books, OffsetScrollPosition.positionFunction(35), true);

		assertThat(this.adapter.getContent(window)).isEqualTo(books);
		assertThat(this.adapter.hasNext(window)).isTrue();
		assertThat(this.adapter.hasPrevious(window)).isTrue();
		assertThat(this.adapter.cursorAt(window, 3)).isEqualTo("O_38");
	}

	@Test
	void unpaged() {
		List<Book> books = BookSource.books();
		Window<Book> window = Window.from(books, OffsetScrollPosition.positionFunction(0));

		assertThat(this.adapter.getContent(window)).isEqualTo(books);
		assertThat(this.adapter.hasNext(window)).isFalse();
		assertThat(this.adapter.hasPrevious(window)).isFalse();
		assertThat(this.adapter.cursorAt(window, 3)).isEqualTo("O_3");
	}

	@Test
	void hasNextPreviousWithKeysetScrollForward() {

		Window<Book> window = Window.from(
				BookSource.books(),
				index -> ScrollPosition.of(Collections.singletonMap("id", index), ScrollPosition.Direction.FORWARD),
				true);

		assertThat(this.adapter.hasPrevious(window)).isFalse();
		assertThat(this.adapter.hasNext(window)).isEqualTo(true);
	}

	@Test
	void hasNextPreviousWithKeysetScrollBackward() {

		Window<Book> window = Window.from(
				BookSource.books(),
				index -> ScrollPosition.of(Collections.singletonMap("id", index), ScrollPosition.Direction.BACKWARD),
				true);

		assertThat(this.adapter.hasPrevious(window)).isEqualTo(true);
		assertThat(this.adapter.hasNext(window)).isFalse();
	}

}

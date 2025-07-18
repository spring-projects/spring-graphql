/*
 * Copyright 2020-present the original author or authors.
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

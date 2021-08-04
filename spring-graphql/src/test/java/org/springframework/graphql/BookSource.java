/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

public class BookSource {

	private static final Map<Long, Book> booksMap = new HashMap<>();

	private static final Map<Long, Author> authorsMap = new HashMap<>();

	static {
		authorsMap.put(1L, new Author(1L, "George", "Orwell"));
		authorsMap.put(2L, new Author(2L, "F. Scott", "Fitzgerald"));
		authorsMap.put(3L, new Author(3L, "Joseph", "Heller"));
		authorsMap.put(4L, new Author(4L, "Virginia", "Woolf"));
		authorsMap.put(5L, new Author(5L, "Douglas", "Adams"));
		authorsMap.put(6L, new Author(6L, "Vince", "Gilligan"));

		booksMap.put(1L, new Book(1L, "Nineteen Eighty-Four", authorsMap.get(1L)));
		booksMap.put(2L, new Book(2L, "The Great Gatsby", authorsMap.get(2L)));
		booksMap.put(3L, new Book(3L, "Catch-22", authorsMap.get(3L)));
		booksMap.put(4L, new Book(4L, "To The Lighthouse", authorsMap.get(4L)));
		booksMap.put(5L, new Book(5L, "Animal Farm", authorsMap.get(1L)));
		booksMap.put(42L, new Book(42L, "Hitchhiker's Guide to the Galaxy", authorsMap.get(5L)));
		booksMap.put(53L, new Book(53L, "Breaking Bad", authorsMap.get(6L)));
	}


	public static Map<Long, Book> booksMap() {
		return booksMap;
	}

	public static List<Book> books() {
		return new ArrayList<>(booksMap.values());
	}

	public static Book getBook(Long id) {
		return booksMap.get(id);
	}

	@SuppressWarnings("ConstantConditions")
	public static List<Book> findBooksByAuthor(String author) {
		return Flux.fromIterable(books())
				.filter((book) -> book.getAuthor().getFullName().contains(author))
				.collectList()
				.block();
	}

	public static Author getAuthor(Long id) {
		return authorsMap.get(id);
	}

}

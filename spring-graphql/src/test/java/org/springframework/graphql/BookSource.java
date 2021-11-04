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
import java.util.function.Function;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class BookSource {

	public static final Resource schema = new ClassPathResource("books/schema.graphqls");


	private static final Map<Long, Book> booksMap = new HashMap<>();

	private static final Map<Long, Book> booksWithoutAuthorsMap;

	private static final Map<Long, Author> authorsMap = new HashMap<>();

	static {
		authorsMap.put(101L, new Author(101L, "George", "Orwell"));
		authorsMap.put(102L, new Author(102L, "F. Scott", "Fitzgerald"));
		authorsMap.put(103L, new Author(103L, "Joseph", "Heller"));
		authorsMap.put(104L, new Author(104L, "Virginia", "Woolf"));
		authorsMap.put(105L, new Author(105L, "Douglas", "Adams"));
		authorsMap.put(106L, new Author(106L, "Vince", "Gilligan"));

		booksMap.put(1L, new Book(1L, "Nineteen Eighty-Four", authorsMap.get(101L)));
		booksMap.put(2L, new Book(2L, "The Great Gatsby", authorsMap.get(102L)));
		booksMap.put(3L, new Book(3L, "Catch-22", authorsMap.get(103L)));
		booksMap.put(4L, new Book(4L, "To The Lighthouse", authorsMap.get(104L)));
		booksMap.put(5L, new Book(5L, "Animal Farm", authorsMap.get(101L)));
		booksMap.put(42L, new Book(42L, "Hitchhiker's Guide to the Galaxy", authorsMap.get(105L)));
		booksMap.put(53L, new Book(53L, "Breaking Bad", authorsMap.get(106L)));

		booksWithoutAuthorsMap = booksMap.values().stream()
				.map(book -> new Book(book.getId(), book.getName(), book.getAuthorId()))
				.collect(Collectors.toMap(Book::getId, Function.identity()));
	}


	public static List<Book> books() {
		return new ArrayList<>(booksMap.values());
	}

	public static List<Book> booksWithoutAuthors() {
		return new ArrayList<>(booksWithoutAuthorsMap.values());
	}

	public static Book getBook(Long id) {
		return booksMap.get(id);
	}

	public static Book getBookWithoutAuthor(Long id) {
		return booksWithoutAuthorsMap.get(id);
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

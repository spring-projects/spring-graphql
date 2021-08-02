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

public class BookSource {

	private static final Map<Long, Book> booksMap = new HashMap<>(4);

	static {
		booksMap.put(1L, new Book(1L, "Nineteen Eighty-Four", new Author("George", "Orwell")));
		booksMap.put(2L, new Book(2L, "The Great Gatsby", new Author("F. Scott", "Fitzgerald")));
		booksMap.put(3L, new Book(3L, "Catch-22", new Author("Joseph", "Heller")));
		booksMap.put(4L, new Book(4L, "To The Lighthouse", new Author("Virginia", "Woolf")));
		booksMap.put(5L, new Book(5L, "Animal Farm", new Author("George", "Orwell")));
		booksMap.put(42L, new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author("Douglas", "Adams")));
		booksMap.put(53L, new Book(53L, "Breaking Bad", new Author("Vince", "Gilligan")));
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

}

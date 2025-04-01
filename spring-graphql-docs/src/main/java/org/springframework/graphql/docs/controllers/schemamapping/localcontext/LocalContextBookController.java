/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.docs.controllers.schemamapping.localcontext;

import java.util.ArrayList;
import java.util.List;

import graphql.GraphQLContext;
import graphql.execution.DataFetcherResult;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.LocalContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

// tag::localcontext[]
@Controller
public class LocalContextBookController {

	@QueryMapping
	public DataFetcherResult<Book> bookById(@Argument Long id) {
		// Our controller method must return a DataFetcherResult
		DataFetcherResult.Builder<Book> resultBuilder = DataFetcherResult.newResult();
		BookAndAuthor bookAndAuthor = this.fetchBookAndAuthorById(id);

		// Create a new local context and store the author value
		GraphQLContext localContext = GraphQLContext.getDefault()
				.put("author", bookAndAuthor.author);
		return resultBuilder
				.data(bookAndAuthor.book)
				.localContext(localContext)
				.build();
	}

	@SchemaMapping
	public List<Book> related(Book book, @LocalContextValue Author author) {
		List<Book> relatedBooks = new ArrayList<>();
		relatedBooks.addAll(fetchBooksByAuthor(author));
		relatedBooks.addAll(fetchSimilarBooks(book));
		return relatedBooks;
	}

	// end::localcontext[]

	private BookAndAuthor fetchBookAndAuthorById(Long id) {
		return new BookAndAuthor(new Book(id, "Spring for GraphQL", 12L),
				new Author(1L, "Jane Doe"));
	}

	private List<Book> fetchBooksByAuthor(Author author) {
		return List.of(new Book(1, "Spring for GraphQL", 12L));
	}

	private List<Book> fetchSimilarBooks(Book book) {
		return List.of();
	}

	record BookAndAuthor(Book book, Author author) {
	}

	record Book(long id, String title, long authorId) {
	}

	record Author(long id, String name) {
	}

}

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

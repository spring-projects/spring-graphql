package org.springframework.graphql;

import java.util.Arrays;
import java.util.List;

import graphql.schema.DataFetcher;

public class GraphQLDataFetchers {

	private static List<Book> books = Arrays.asList(
			new Book("book-1", "GraphQL for beginners", 100, "John GraphQL"),
			new Book("book-2", "Harry Potter and the Philosopher's Stone", 223, "Joanne Rowling"),
			new Book("book-3", "Moby Dick", 635, "Moby Dick"),
			new Book("book-3", "Moby Dick", 635, "Moby Dick"));


	public static DataFetcher getBookByIdDataFetcher() {
		return dataFetchingEnvironment -> {
			String bookId = dataFetchingEnvironment.getArgument("id");
			return books
					.stream()
					.filter(book -> book.getId().equals(bookId))
					.findFirst()
					.orElse(null);
		};
	}
}

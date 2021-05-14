package org.springframework.graphql.web;

import java.util.Arrays;
import java.util.List;

import graphql.schema.DataFetcher;
import reactor.core.publisher.Flux;

public class GraphQlDataFetchers {

	private static List<Book> books = Arrays.asList(
			new Book("book-1", "GraphQL for beginners", 100, "John GraphQL"),
			new Book("book-2", "Harry Potter and the Philosopher's Stone", 223, "Joanne Rowling"),
			new Book("book-3", "Moby Dick", 635, "Moby Dick"),
			new Book("book-3", "Moby Dick", 635, "Moby Dick"));


	public static DataFetcher getBookByIdDataFetcher() {
		return env -> books.stream()
				.filter(book -> book.getId().equals(env.getArgument("id")))
				.findFirst()
				.orElse(null);
	}

	public static DataFetcher getBooksOnSale() {
		return env -> Flux.fromIterable(books)
				.filter(book -> book.getPageCount() >= (int) env.getArgument("minPages"));
	}

}

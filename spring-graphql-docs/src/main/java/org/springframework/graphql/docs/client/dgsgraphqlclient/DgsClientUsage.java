/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.dgsgraphqlclient;


import java.util.List;

import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.DgsGraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

public class DgsClientUsage {

	public void sendSingleQuery() {
		// tag::sendSingleQuery[]
		HttpGraphQlClient client = HttpGraphQlClient.create(WebClient.create("https://example.org/graphql"));
		DgsGraphQlClient dgsClient = DgsGraphQlClient.create(client); // <1>

		List<Book> books = dgsClient.request(BookByIdGraphQLQuery.newRequest().id("42").build()) // <2>
				.projection(new BooksProjectionRoot<>().id().name()) // <3>
				.retrieveSync("books")
				.toEntityList(Book.class);
		// end::sendSingleQuery[]
	}

	public void sendManyQueries() {
		// tag::sendManyQueries[]
		HttpGraphQlClient client = HttpGraphQlClient.create(WebClient.create("https://example.org/graphql"));
		DgsGraphQlClient dgsClient = DgsGraphQlClient.create(client); // <1>

		ClientGraphQlResponse response = dgsClient
				.request(BookByIdGraphQLQuery.newRequest().id("42").build()) // <2>
				.queryAlias("firstBook")  // <3>
				.projection(new BooksProjectionRoot<>().id().name())
				.request(BookByIdGraphQLQuery.newRequest().id("53").build()) // <4>
				.queryAlias("secondBook")
				.projection(new BooksProjectionRoot<>().id().name())
				.executeSync(); // <5>

		Book firstBook = response.field("firstBook").toEntity(Book.class); // <6>
		Book secondBook = response.field("secondBook").toEntity(Book.class);
		// end::sendManyQueries[]
	}

	record Book(Long id, String name) {

	}
}

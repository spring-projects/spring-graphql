/*
 * Copyright 2025-present the original author or authors.
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

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import org.junit.jupiter.api.Test;

import org.springframework.graphql.Book;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DgsGraphQlClient}.
 *
 * @author Brian Clozel
 */
class DgsGraphQlClientTests extends GraphQlClientTestSupport {

	protected DgsGraphQlClient dgsGraphQlClient() {
		return DgsGraphQlClient.create(graphQlClient());
	}

	@Test
	void retrieveEntity() {
		var document = """
                {
                  bookById(id: "42")
                }""";
		getGraphQlService().setDataAsJson(document, """
                	{"bookById":{"id":"42","name":"Hitchhiker's Guide to the Galaxy"}}
                """);
		var book = dgsGraphQlClient()
				.request(BookByIdGraphQLQuery.newRequest().id("42").build())
				.retrieveSync()
				.toEntity(Book.class);

		assertThat(book.getId()).isEqualTo(42L);
		assertThat(book.getName()).isEqualTo("Hitchhiker's Guide to the Galaxy");
	}

	@Test
	void multiQueryRequest() {
		getGraphQlService().setDataAsJson("""
                        {
                          first: bookById(id: "42") {
                            id
                            name
                          }
                          second: bookById(id: "53") {
                            id
                            name
                          }
                        }""",
				"""
							{
								"first" : {"id":"42","name":"Hitchhiker's Guide to the Galaxy"},
								"second" : {"id":"53","name":"Breaking Bad"}
							}
						""");

		var secondQuery = BookByIdGraphQLQuery.newRequest().id("53").build();
		secondQuery.setQueryAlias("second");
		ClientGraphQlResponse response = dgsGraphQlClient()
				.request(BookByIdGraphQLQuery.newRequest().id("42").build())
				.projection(new BooksProjectionRoot<>().id().name())
				.queryAlias("first")
				.request(BookByIdGraphQLQuery.newRequest().id("53").build())
				.projection(new BooksProjectionRoot<>().id().name())
				.queryAlias("second")
				.executeSync();

		Book firstBook = response.field("first").toEntity(Book.class);
		assertThat(firstBook.getId()).isEqualTo(42L);
		assertThat(firstBook.getName()).isEqualTo("Hitchhiker's Guide to the Galaxy");
		Book secondBook = response.field("second").toEntity(Book.class);
		assertThat(secondBook.getId()).isEqualTo(53);
		assertThat(secondBook.getName()).isEqualTo("Breaking Bad");
	}

}

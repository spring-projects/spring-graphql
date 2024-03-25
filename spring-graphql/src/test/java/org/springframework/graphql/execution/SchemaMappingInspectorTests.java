/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.execution;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SchemaMappingInspector}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @author Oliver Drotbohm
 */
class SchemaMappingInspectorTests extends SchemaMappingInspectorTestSupport{


	@Nested
	class QueryTests {

		@Test
		void reportHasUnmappedQuery() {
			String schema = """
						type Query {
							greeting: String
						}
					""";
			SchemaReport report = inspectSchema(schema, EmptyController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Query", "greeting");
		}

		@Test
		void reportIsEmptyWhenQueryIsMapped() {
			String schema = """
						type Query {
							greeting: String
						}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void reportWorksForQueryWithList() {
			String schema = """
						type Query {
							allBooks: [Book]
						}
						
						type Book {
							id: ID
							name: String
							missing: Boolean
					 	}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Book", "missing");
		}

		@Test
		void reportWorksForQueryWithOptional() {
			String schema = """
						type Query {
							optionalBook: Book
						}

						type Book {
							id: ID
							name: String
							missing: Boolean
						}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Book", "missing");
		}

		@Test
		void reportWorksForQueryWithConnection() {
			String schema = """
						type Query {
							paginatedBooks: BookConnection
						}
						
						type BookConnection {
							edges: [BookEdge]!
							pageInfo: PageInfo!
						}

						type BookEdge {
							cursor: String!
							# ...
						}

						type PageInfo {
							startCursor: String
							# ...
						}

						type Book {
							id: ID
							name: String
							missing: Boolean
					 	}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Book", "missing");
		}

		@Test
		void reportWorksForQueryWithExtensionType() {
			String schema = """
						type Query {
						}
						extend type Query {
					    	greeting: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, EmptyController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Query", "greeting");
		}

	}


	@Nested
	class MutationTests {

		@Test
		void reportContainsUnmappedMutation() {
			String schema = """
						type Query{
							greeting: String
						}
						type Mutation {
							createBook: Book
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Mutation", "createBook");
		}

		@Test
		void reportIsEmptyWhenMutationIsMapped() {
			String schema = """
						type Query{
							greeting: String
						}
						type Mutation {
							createBook: Book
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(0);
		}

		@Test
		void reportWorksForMutationWithExtensionType() {
			String schema = """
						type Query {
							greeting: String
						}
						type Mutation {
						}
						extend type Mutation {
					    	createBook: Book
					 	}
					 	type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Mutation", "createBook");
		}

	}


	@Nested
	class SubscriptionTests {

		@Test
		void unmappedSubscription() {
			String schema = """
						type Query{
							greeting: String
						}
						type Subscription {
							bookSearch(author: String) : [Book!]!
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Subscription", "bookSearch");
		}

		@Test
		void unmappedSubscriptionWithExtensionType() {
			String schema = """
						type Query{
							greeting: String
						}
						type Subscription {
						}
						extend type Subscription {
					    	bookSearch(author: String) : [Book!]!
					 	}
					 	type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Subscription", "bookSearch");
		}

		@Test
		void mappedSubscriptionWithUnmappedArgument() {
			String schema = """
						type Query{
							greeting: String
						}
						type Subscription {
							bookSearch(author: String) : [Book!]!
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(0);
			assertThatReport(report).hasUnmappedArgumentCount(1).containsUnmappedArguments("myAuthor");
		}

	}


	@Nested
	class TypesInspectionTests {

		@Test
		void reportIsEmptyWhenFieldHasMatchingObjectProperty() {
			String schema = """
						type Query {
							bookById(id: ID): Book
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(0);
		}

		@Test
		void reportIsEmptyWhenFieldHasDataFetcherMapping() {
			String schema = """
						type Query {
							bookById(id: ID): Book
						}
						
						type Book {
							id: ID
							name: String
							fetcher: String
					 	}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(0);
		}

		@Test
		void reportIsEmptyWhenFieldHasBatchMapping() {
			String schema = """
						type Query {
							books: [Book]
						}
						
						type Book {
							id: ID
							name: String
							author: Author
					 	}
					 	
						type Author {
							id: ID
							firstName: String
							lastName: String
						}
					""";
			SchemaReport report = inspectSchema(schema, BatchMappingBookController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(0);
		}

		@Test
		void reportHasUnmappedField() {
			String schema = """
						type Query {
							bookById(id: ID): Book
						}
						
						type Book {
							id: ID
							name: String
							missing: Boolean
					 	}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Book", "missing");
		}

		@Test
		void reportHasUnmappedDataFetcher() {
			String schema = """
						type Query {
							anything: String
						}
					""";
			SchemaReport report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedDataFetcherCount(1).containsUnmappedDataFetchersFor("Query", "greeting");
		}

		@Test
		void reportHasUnmappedFieldOnNestedType() {
			String schema = """
						type Query {
							bookById(id: ID): Book
						}
						
						type Book {
							id: ID
							name: String
							author: Author
					 	}
					 	
						type Author {
							id: ID
							firstName: String
							missing: String
						}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Author", "missing");
		}

		@Test
		void reportWorksWithCyclicRelations() {
			String schema = """
						type Query {
							teamById(id: ID): Team
						}
						
						type Team {
							name: String
							members: [TeamMember]
					 	}
					 	
					 	type TeamMember {
							name: String
							team: Team
							missing: String
						}
					""";
			SchemaReport report = inspectSchema(schema, TeamController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("TeamMember", "missing");
		}

		@Test
		void reportWorksWithPropertyOnTypeExtension() {
			String schema = """
						type Query {
							bookById(id: ID): Book
						}
						
						type Book {
							id: ID
							name: String
					 	}
					 	extend type Book {
							missing: Boolean
					 	}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Book", "missing");
		}

		@Test
		void reportHasSkippedTypeForUnknownDataFetcherType() {
			String schemaContent = """
						type Query {
							bookById(id: ID): Book
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";

			GraphQLSchema schema = SchemaGenerator.createdMockedSchema(schemaContent);
			RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
					.type("Query", builder -> builder.dataFetcher("bookById", environment -> null))
					.build();

			SchemaReport report = SchemaMappingInspector.inspect(schema, wiring);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(1).containsSkippedTypes("Book");
		}

		@Test
		void reportHasSkippedTypeForObjectReturnType() {
			String schemaContent = """
						type Query {
							bookObject(id: ID): Book
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaReport report = inspectSchema(schemaContent, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(1).containsSkippedTypes("Book");
		}

		@Test
		void reportIsEmptyIfUnknownDataFetcherReturnsSimpleType() {
			String schemaContent = """
						type Query {
							greeting: String
						}
					""";

			GraphQLSchema schema = SchemaGenerator.createdMockedSchema(schemaContent);
			RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
					.type("Query", builder -> builder.dataFetcher("greeting", environment -> null))
					.build();

			SchemaReport report = SchemaMappingInspector.inspect(schema, wiring);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(0);
		}

	}


	@Nested
	class ReportFormatTests {

		@Test
		void reportUnmappedField() {
			String schema = """
						type Query {
							allBooks: [Book]
						}
						type Mutation {
							createBook: Book
						}
						type Subscription {
							bookSearch(author: String) : [Book!]!
						}
						type Book {
							id: ID
							name: String
							missing: Boolean
							author: Author
					 	}
						type Author {
							id: ID
						}
					""";
			SchemaReport report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).hasSkippedTypeCount(0);
			assertThat(report.toString()).contains(
					"GraphQL schema inspection:",
					"Unmapped fields: {Book=[missing]}",
					"Unmapped registrations:",
					"Book.fetcher=BookController#fetcher[1 args]",
					"Query.paginatedBooks=BookController#paginatedBooks[0 args]",
					"Query.bookObject=BookController#bookObject[1 args]",
					"Query.bookById=BookController#bookById[1 args]",
					"{BookController#bookSearch[1 args]=[myAuthor]}",
					"Skipped types: []");
		 }

	}


	@Controller
	static class EmptyController {

	}


	@Controller
	static class GreetingController {

		@QueryMapping
		String greeting() {
			return "Hello";
		}
	}


	@Controller
	static class BookController {

		@QueryMapping
		public Book bookById(@Argument Long id) {
			return new Book();
		}

		@QueryMapping
		public Optional<Book> optionalBook() {
			return Optional.of(new Book());
		}

		@SchemaMapping
		public Author author(Book book) {
			return new Author();
		}

		@QueryMapping
		public List<Book> allBooks() {
			return List.of(new Book());
		}

		@QueryMapping
		public Window<Book> paginatedBooks() {
			return Window.from(List.of(new Book()), ScrollPosition::offset);
		}

		@SchemaMapping
		public String fetcher(Book book) {
			return "custom fetcher";
		}

		@QueryMapping
		public Object bookObject(@Argument Long id) {
			return new Book();
		}

		@MutationMapping
		public Book createBook() {
			return new Book();
		}

		@SubscriptionMapping
		public Flux<List<Book>> bookSearch(@Argument String myAuthor) {
			return Flux.empty();
		}
	}


	@Controller
	private static class BatchMappingBookController {

		@QueryMapping
		public List<Book> books() {
			return Collections.emptyList();
		}

		@BatchMapping
		public Mono<Map<Book, Author>> author(List<Book> books) {
			return Mono.empty();
		}
	}


	@Controller
	static class TeamController {
		@QueryMapping
		public Team teamById(@Argument Long id) {
			return new Team("spring", Collections.emptyList());
		}

		@SchemaMapping
		public CompletableFuture<List<TeamMember>> members(Team team) {
			return CompletableFuture.completedFuture(List.of());
		}

		@SchemaMapping
		public Team team(TeamMember teamMember) {
			return null;
		}

	}


	record Team(String name, List<TeamMember> members) {

	}


	record TeamMember(String name, Team team) {

	}

}
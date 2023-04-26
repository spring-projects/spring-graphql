/*
 * Copyright 2020-2023 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SchemaMappingInspector}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 */
class SchemaMappingInspectorTests {


	@Nested
	class QueryTests {

		@Test
		void reportHasUnmappedQuery() {
			String schema = """
						type Query {
							greeting: String
						}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, EmptyController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Query", "greeting");
		}

		@Test
		void reportIsEmptyWhenQueryIsMapped() {
			String schema = """
						type Query {
							greeting: String
						}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, EmptyController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class, BookController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Mutation", "createBook");
		}

	}


	@Nested
	class SubscriptionTests {

		@Test
		void reportContainsUnmappedSubscription() {
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
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Subscription", "bookSearch");
		}

		@Test
		void reportIsEmptyWhenSubscriptionIsMapped() {
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
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(0);
		}

		@Test
		void reportWorksForSubscriptionWithExtensionType() {
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
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Subscription", "bookSearch");
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BatchMappingBookController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Book", "missing");
		}

		@Test
		void reportHasUnmappedDataFetcher() {
			String schema = """
						type Query {
							anything: String
						}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, TeamController.class);
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
			SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).containsUnmappedFields("Book", "missing");
		}

		@Test
		void reportHasSkippedUnionType() {
			String schema = """
						type Query {
							fooBar: FooBar
						}

						union FooBar = Foo | Bar

						type Foo {
							name: String
					 	}

						type Bar {
							name: String
					 	}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, UnionController.class);
			assertThatReport(report).hasUnmappedFieldCount(0).hasSkippedTypeCount(1).containsSkippedTypes("FooBar");
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

			SchemaMappingInspector.Report report = SchemaMappingInspector.inspect(schema, wiring);
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
			SchemaMappingInspector.Report report = inspectSchema(schemaContent, BookController.class);
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

			SchemaMappingInspector.Report report = SchemaMappingInspector.inspect(schema, wiring);
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
			 SchemaMappingInspector.Report report = inspectSchema(schema, BookController.class);
			assertThatReport(report).hasUnmappedFieldCount(1).hasSkippedTypeCount(0);
			 assertThat(report.toString()).isEqualTo("""
					GraphQL schema inspection:
						Unmapped fields: {Book=[missing]}
						Unmapped DataFetcher registrations: {Book.fetcher=BookController#fetcher[1 args], Query.paginatedBooks=BookController#paginatedBooks[0 args], Query.bookObject=BookController#bookObject[1 args], Query.bookById=BookController#bookById[1 args]}
						Skipped types: []""");
		 }

	}

	private SchemaMappingInspector.Report inspectSchema(String schemaContent, Class<?>... controllers) {
		GraphQLSchema schema = SchemaGenerator.createdMockedSchema(schemaContent);
		RuntimeWiring runtimeWiring = createRuntimeWiring(controllers);
		return SchemaMappingInspector.inspect(schema, runtimeWiring);
	}

	private RuntimeWiring createRuntimeWiring(Class<?>... controllerTypes) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		for (Class<?> controllerType : controllerTypes) {
			context.registerBean(controllerType);
		}
		context.registerBean(BatchLoaderRegistry.class, () -> new DefaultBatchLoaderRegistry());
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		configurer.configure(wiringBuilder);
		return wiringBuilder.build();
	}

	static SchemaInspectionReportAssert assertThatReport(SchemaMappingInspector.Report actual) {
		return new SchemaInspectionReportAssert(actual);
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
			return Window.from(List.of(new Book()), OffsetScrollPosition::of);
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
		public Flux<List<Book>> bookSearch(@Argument String author) {
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


	@Controller
	static class UnionController {

		@QueryMapping
		Object fooBar() {
			return "Hello";
		}
	}


	private static class SchemaInspectionReportAssert
			extends AbstractAssert<SchemaInspectionReportAssert, SchemaMappingInspector.Report> {

		public SchemaInspectionReportAssert(SchemaMappingInspector.Report actual) {
			super(actual, SchemaInspectionReportAssert.class);
		}

		public void isEmpty() {
			isNotNull();
			if (!this.actual.unmappedFields().isEmpty()) {
				failWithMessage("Report contains missing fields: %s", this.actual.unmappedFields());
			}
			if (!this.actual.unmappedDataFetchers().isEmpty()) {
				failWithMessage("Report contains missing DataFetcher registrations for %s", this.actual.unmappedDataFetchers());
			}
			if (!this.actual.skippedTypes().isEmpty()) {
				failWithMessage("Report contains skipped types: %s", this.actual.skippedTypes());
			}
		}

		public SchemaInspectionReportAssert hasUnmappedFieldCount(int expected) {
			isNotNull();
			Integer actual = this.actual.unmappedFields().values().stream().map(List::size).reduce(0, Integer::sum);
			if (actual != expected) {
				failWithMessage("Expected %s unmapped fields, found %s.", expected, this.actual.unmappedFields());
			}
			return this;
		}

		public SchemaInspectionReportAssert hasUnmappedDataFetcherCount(int expected) {
			isNotNull();
			if (this.actual.unmappedDataFetchers().size() != expected) {
				failWithMessage("Expected %s unmapped fields, found %s.", expected, this.actual.unmappedFields());
			}
			return this;
		}

		public SchemaInspectionReportAssert hasSkippedTypeCount(int expected) {
			isNotNull();
			if (this.actual.skippedTypes().size() != expected) {
				failWithMessage("Expected %s skipped types, found %s.", expected, this.actual.skippedTypes());
			}
			return this;
		}

		public SchemaInspectionReportAssert containsUnmappedFields(String typeName, String... fieldNames) {
			isNotNull();
			List<String> expected = Arrays.asList(fieldNames);
			List<String> actual = this.actual.unmappedFields().get(typeName);
			if (actual == null || !actual.containsAll(expected)) {
				failWithMessage("Expected unmapped fields for %s: %s, found %s", typeName, expected, actual);
			}
			return this;
		}

		public SchemaInspectionReportAssert containsUnmappedDataFetchersFor(String typeName, String... fieldNames) {
			isNotNull();
			List<FieldCoordinates> expected = Arrays.stream(fieldNames)
					.map(field -> FieldCoordinates.coordinates(typeName, field)).toList();
			if (!this.actual.unmappedDataFetchers().keySet().containsAll(expected)) {
				failWithMessage("Expected unmapped DataFetchers for %s, found %s",
						expected, this.actual.unmappedDataFetchers());
			}
			return this;
		}

		public SchemaInspectionReportAssert containsSkippedTypes(String... typeNames) {
			isNotNull();
			List<String> expected = Arrays.asList(typeNames);
			Set<String> actual = this.actual.skippedTypes();
			if (!actual.containsAll(expected)) {
				failWithMessage("Expected skipped types: %s, found %s", expected, actual);
			}
			return this;
		}
	}

}
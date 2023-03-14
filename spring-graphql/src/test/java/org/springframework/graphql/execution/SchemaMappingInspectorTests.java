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

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.Argument;
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
 */
class SchemaMappingInspectorTests {


	@Nested
	class QueriesInspectionTests {

		@Test
		void hasMissingQueryEntryWhenMissingQueryMapping() {
			String schema = """
						type Query {
							greeting: String
						}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, EmptyController.class);
			assertThatReport(report).hasSize(1).missesOperations("Query", "greeting");
		}

		@Test
		void reportIsEmptyWhenQueryMapping() {
			String schema = """
						type Query {
							greeting: String
						}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void inspectTypeForCollections() {
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
			assertThatReport(report).hasSize(1).missesFields("Book", "missing");
		}

		@Test
		void inspectExtensionTypesForQueries() {
			String schema = """
						type Query {
						}
						extend type Query {
					    	greeting: String
					 	}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, EmptyController.class);
			assertThatReport(report).hasSize(1).missesOperations("Query", "greeting");
		}

	}

	@Nested
	class MutationInspectionTests {
		@Test
		void hasMissingOperationEntryWhenMissingQueryMapping() {
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
			assertThatReport(report).hasSize(1).missesOperations("Mutation", "createBook");
		}

		@Test
		void reportIsEmptyWhenMutationMapping() {
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
			assertThatReport(report).isEmpty();
		}

		@Test
		void inspectExtensionTypesForMutations() {
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
			assertThatReport(report).hasSize(1).missesOperations("Mutation", "createBook");
		}

	}

	@Nested
	class SubscriptionInspectionTests {
		@Test
		void hasMissingOperationEntryWhenMissingSubscriptionMapping() {
			String schema = """
						type Query{
							greeting: String
						}
						type Subscription {
							bookSearch(author: String) : Book!
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasSize(1).missesOperations("Subscription", "bookSearch");
		}

		@Test
		void reportIsEmptyWhenSubscriptionMapping() {
			String schema = """
						type Query{
							greeting: String
						}
						type Subscription {
							bookSearch(author: String) : Book!
						}
						
						type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class, BookController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void inspectExtensionTypesForSubscriptions() {
			String schema = """
						type Query{
							greeting: String
						}
						type Subscription {
						}
						extend type Subscription {
					    	bookSearch(author: String) : Book!
					 	}
					 	type Book {
							id: ID
							name: String
					 	}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, GreetingController.class);
			assertThatReport(report).hasSize(1).missesOperations("Subscription", "bookSearch");
		}

	}

	@Nested
	class TypesInspectionTests {
		@Test
		void reportIsEmptyWhenPropertyOnType() {
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
			assertThatReport(report).isEmpty();
		}

		@Test
		void reportIsEmptyWhenDataFetcherForField() {
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
			assertThatReport(report).isEmpty();
		}

		@Test
		void hasMissingFieldEntryWhenMissingPropertyOnType() {
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
			assertThatReport(report).hasSize(1).missesFields("Book", "missing");
		}

		@Test
		void hasMissingFieldEntryWhenMissingPropertyOnNestedType() {
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
			assertThatReport(report).hasSize(1).missesFields("Author", "missing");
		}

		@Test
		void cyclicRelationBetweenTypesDoNotFail() {
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
						}
					""";
			SchemaMappingInspector.Report report = inspectSchema(schema, TeamController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void hasMissingFieldEntryWhenMissingPropertyOnTypeProvidedByExtension() {
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
			assertThatReport(report).hasSize(1).missesFields("Book", "missing");
		}

	}

	@Nested
	class ReportFormatTests {

		 @Test
		void reportsMissingQuery() {
			 String schema = """
						type Query {
							greeting: String
						}
					""";
			 SchemaMappingInspector.Report report = inspectSchema(schema, EmptyController.class);
			 assertThat(report.getSummary()).isEqualTo("GraphQL schema inspection found missing mappings for: Query[greeting].");
		 }

		 @Test
		void reportMissingField() {
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
			 assertThat(report.getSummary()).isEqualTo("GraphQL schema inspection found missing mappings for: Book[missing].");
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

		@SchemaMapping
		public Author author(Book book) {
			return new Author();
		}

		@QueryMapping
		public List<Book> allBooks() {
			return List.of(new Book());
		}

		@SchemaMapping
		public String fetcher(Book book) {
			return "custom fetcher";
		}

		@MutationMapping
		public Book createBook() {
			return new Book();
		}

		@SubscriptionMapping
		public Mono<Book> bookSearch(@Argument String author) {
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
		public List<TeamMember> members(Team team) {
			return List.of();
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

	SchemaMappingInspector.Report inspectSchema(String schemaContent, Class<?>... controllers) {
		GraphQLSchema schema = SchemaGenerator.createdMockedSchema(schemaContent);
		RuntimeWiring.Builder builder = createRuntimeWiring(controllers);
		return new SchemaMappingInspector().inspectSchemaMappings(schema, builder.build());
	}

	RuntimeWiring.Builder createRuntimeWiring(Class<?>... handlerTypes) {
		AnnotationConfigApplicationContext appContext = new AnnotationConfigApplicationContext();
		for (Class<?> handlerType : handlerTypes) {
			appContext.registerBean(handlerType);
		}
		appContext.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(appContext);
		configurer.afterPropertiesSet();

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		configurer.configure(wiringBuilder);
		return wiringBuilder;
	}

	static SchemaInspectionReportAssert assertThatReport(SchemaMappingInspector.Report actual) {
		return new SchemaInspectionReportAssert(actual);
	}

	static class SchemaInspectionReportAssert extends AbstractAssert<SchemaInspectionReportAssert, SchemaMappingInspector.Report> {

		public SchemaInspectionReportAssert(SchemaMappingInspector.Report actual) {
			super(actual, SchemaInspectionReportAssert.class);
		}

		public void isEmpty() {
			isNotNull();
			if (!this.actual.missingOperations().isEmpty()) {
				failWithMessage("Report contains missing operations for %s",
						this.actual.missingOperations().keySet());
			}
			if (!this.actual.missingFields().isEmpty()) {
				failWithMessage("Report contains missing fields for %s",
						this.actual.missingFields().keySet());
			}
		}

		public SchemaInspectionReportAssert hasSize(int size) {
			isNotNull();
			Integer missingOps = this.actual.missingOperations().values().stream().map(List::size).reduce(0, Integer::sum);
			Integer missingFields = this.actual.missingFields().values().stream().map(List::size).reduce(0, Integer::sum);
			if ((missingOps + missingFields) != size) {
				failWithMessage("Expected report with %s entries, found %d.", size, (missingOps + missingFields));
			}
			return this;
		}

		public SchemaInspectionReportAssert missesOperations(String operationType, String... names) {
			isNotNull();
			List<String> expectedOperations = Arrays.asList(names);
			List<String> actualOperations = this.actual.missingOperations().get(operationType);
			if (actualOperations != null) {
				if (!actualOperations.containsAll(expectedOperations)) {
					failWithMessage("Expected missing DataFetchers for %s: %s, found %s", operationType, expectedOperations, actualOperations);
				}
			}
			else {
				failWithMessage("No missing DataFetcher for %s", operationType);
			}
			return this;
		}

		public SchemaInspectionReportAssert missesFields(String typeName, String... fieldNames) {
			isNotNull();
			List<String> expectedFields = Arrays.asList(fieldNames);
			List<String> actualFields = this.actual.missingFields().get(typeName);
			if (actualFields != null) {
				if (!actualFields.containsAll(expectedFields)) {
					failWithMessage("Expected missing fields for %s: %s, found %s", typeName, expectedFields, actualFields);
				}
			}
			else {
				failWithMessage("No missing field for %s", typeName);
			}
			return this;
		}
	}
}
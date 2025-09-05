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

package org.springframework.graphql.execution;

import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SchemaMappingInspector} {@link org.springframework.core.Nullness} support.
 *
 * @author Brian Clozel
 */
class SchemaMappingInspectorNullnessTests extends SchemaMappingInspectorTestSupport {


	@Nested
	class PropertiesNullnessTests {

		@Test
		void reportIsEmptyWhenSchemaFieldNullableAndTypeFieldUnspecified() {
			String schema = """
						type Query {
							bookById(id: ID): DefaultBook
						}
						type DefaultBook {
							id: ID
							title: String
						}
					""";
			SchemaReport report = inspectSchema(schema, DefaultBookController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void reportIsEmptyWhenSchemaFieldNonNullAndTypeFieldUnspecified() {
			String schema = """
						type Query {
							bookById(id: ID): DefaultBook
						}
						type DefaultBook {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, DefaultBookController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void reportIsEmptyWhenSchemaFieldNonNullAndTypeFieldNonNull() {
			String schema = """
						type Query {
							bookById(id: ID): NonNullClassLevelBook
						}
						type NonNullClassLevelBook {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NonNullClassLevelFieldBookController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void reportHasEntriesWhenSchemaFieldNonNullAndFieldMethodsNullable() {
			String schema = """
						type Query {
							bookById(id: ID): NullableRecordFieldsBook
						}
						type NullableRecordFieldsBook {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableRecordFieldsBookController.class);
			assertThatReport(report).containsFieldsNullnessMismatches("NullableRecordFieldsBook", "id", "title");
		}

		@Test
		void reportFormatWhenSchemaFieldNonNullAndFieldMethodsNullable() {
			String schema = """
						type Query {
							bookById(id: ID): NullableRecordFieldsBook
						}
						type NullableRecordFieldsBook {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableRecordFieldsBookController.class);
			assertThat(report.toString()).contains(
					"NullableRecordFieldsBook=",
					"id is NON_NULL -> 'NullableRecordFieldsBook#id' is NULLABLE",
					"title is NON_NULL -> 'NullableRecordFieldsBook#title' is NULLABLE"
			);
		}

		@Test
		void reportHasEntriesWhenSchemaFieldNullableAndFieldTypeNonNull() {
			String schema = """
						type Query {
							bookById(id: ID): NonNullFieldBook
						}
						type NonNullFieldBook {
							id: ID
							title: String
						}
					""";
			SchemaReport report = inspectSchema(schema, NonNullFieldBookController.class);
			assertThatReport(report).containsFieldsNullnessMismatches("NonNullFieldBook", "id", "title");
		}


		@Test
		void reportHasEntriesWhenSchemaFieldNonNullAndFieldNullable() {
			String schema = """
						type Query {
							bookById(id: ID): NullableFieldBook
						}
						type NullableFieldBook {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableFieldBookController.class);
			assertThatReport(report).containsFieldsNullnessMismatches("NullableFieldBook", "title");
		}

		@Test
		void reportFormatWhenSchemaFieldNonNullAndFieldNullable() {
			String schema = """
						type Query {
							bookById(id: ID): NullableFieldBook
						}
						type NullableFieldBook {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableFieldBookController.class);
			assertThat(report.toString()).contains("{NullableFieldBook=[title is NON_NULL -> 'NullableFieldBook#title' is NULLABLE]}");
		}


		@Controller
		@NullUnmarked
		static class DefaultBookController {

			@QueryMapping
			public DefaultBook bookById(String id) {
				return new DefaultBook("42", "Spring for GraphQL Book");
			}

		}

		@NullUnmarked
		record DefaultBook(String id, String title) {

		}

		@Controller
		@NullUnmarked
		static class NonNullClassLevelFieldBookController {

			@QueryMapping
			public NonNullClassLevelBook bookById(String id) {
				return new NonNullClassLevelBook("42", "Spring for GraphQL Book");
			}

		}

		@NullMarked
		record NonNullClassLevelBook(String id, String title) {

		}

		@Controller
		@NullUnmarked
		static class NullableRecordFieldsBookController {

			@QueryMapping
			public NullableRecordFieldsBook bookById(String id) {
				return new NullableRecordFieldsBook(null, null);
			}

		}

		record NullableRecordFieldsBook(@Nullable String id, @Nullable String title) {

		}

		@Controller
		@NullUnmarked
		static class NullableFieldBookController {

			@QueryMapping
			public NullableFieldBook bookById(String id) {
				return new NullableFieldBook();
			}

		}

		@NullUnmarked
		static class NullableFieldBook {

			public String id;

			public @Nullable String title;

		}

		@Controller
		static class NonNullFieldBookController {

			@QueryMapping
			public NonNullFieldBook bookById(String id) {
				return new NonNullFieldBook();
			}

		}

		static class NonNullFieldBook {

			public String id;

			public String title;

		}

	}

	@Nested
	class ReturnTypesNullnessTests {

		@Test
		void reportIsEmptyWhenSchemaFieldNullableAndReturnTypeUnspecified() {
			String schema = """
						type Query {
							bookById(id: ID): Book
						}
						type Book {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, DefaultBookController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void reportIsEmptyWhenSchemaFieldNullableAndReturnTypeNullable() {
			String schema = """
						type Query {
							bookById(id: ID): Book
						}
						type Book {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableBookController.class);
			assertThatReport(report).isEmpty();
		}

		@Test
		void reportIsEmptyWhenSchemaFieldNonNullAndReturnTypeNonNull() {
			String schema = """
						type Query {
							bookById(id: ID): Book!
						}
						type Book {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NonNullBookController.class);
			assertThatReport(report).isEmpty();
		}


		@Test
		void reportHasEntryWhenSchemaFieldNonNullAndReturnTypeNullable() {
			String schema = """
						type Query {
							bookById(id: ID): Book!
						}
						type Book {
							id: ID
							title: String
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableBookController.class);
			assertThatReport(report).containsFieldsNullnessMismatches("Query", "bookById");
		}

		@Test
		void reportFormatWhenSchemaFieldNonNullAndReturnTypeNullable() {
			String schema = """
						type Query {
							bookById(id: ID): Book!
						}
						type Book {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableBookController.class);
			assertThat(report.toString())
					.contains("{Query=[bookById is NON_NULL -> 'NullableBookController#bookById[1 args]' is NULLABLE]}");
		}

		@Test
		void reportIsEmptyWhenSchemaFieldNonNullAndAsyncTypeNullable() {
			String schema = """
						type Query {
							bookById(id: ID): Book!
						}
						type Book {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableAsyncBookController.class);
			assertThatReport(report).isEmpty();
		}

		@Controller
		@NullUnmarked
		static class DefaultBookController {

			@QueryMapping
			public Book bookById(String id) {
				return new Book("42", "Spring for GraphQL Book");
			}

		}

		@Controller
		@NullUnmarked
		static class NullableBookController {

			@QueryMapping
			public @Nullable Book bookById(String id) {
				return new Book("42", "Spring for GraphQL Book");
			}

		}

		@Controller
		@NullUnmarked
		static class NonNullBookController {

			@QueryMapping
			public @NonNull Book bookById(String id) {
				return new Book("42", "Spring for GraphQL Book");
			}

		}




		@Controller
		@NullUnmarked
		static class NullableAsyncBookController {

			@QueryMapping
			public @Nullable CompletableFuture<Book> bookById(String id) {
				return CompletableFuture.completedFuture(null);
			}

		}

		record Book(String id, String title) {

		}

	}

	@Nested
	class ArgumentsNullnessTests {

		@Test
		void reportHasEntryWhenSchemaFieldNonNullAndReturnTypeNullable() {
			String schema = """
						type Query {
							bookById(id: ID!): Book!
						}
						type Book {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableArgBookController.class);
			assertThatReport(report).containsArgumentsNullnessMismatches("java.lang.String id");
		}

		@Test
		void reportFormatWhenSchemaFieldNonNullAndReturnTypeNullable() {
			String schema = """
						type Query {
							bookById(id: ID!): Book!
						}
						type Book {
							id: ID!
							title: String!
						}
					""";
			SchemaReport report = inspectSchema(schema, NullableArgBookController.class);
			assertThat(report.toString())
					.contains("{NullableArgBookController#bookById[1 args]=[java.lang.String id should be NON_NULL]}");
		}


		@Controller
		@NullUnmarked
		static class NullableArgBookController {

			@QueryMapping
			public @NonNull Book bookById(@Nullable String id) {
				return new Book("42", "Spring for GraphQL Book");
			}

		}

		record Book(String id, String title) {

		}

	}

}

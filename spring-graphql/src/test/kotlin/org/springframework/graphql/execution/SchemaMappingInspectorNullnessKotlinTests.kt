/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution

import org.junit.jupiter.api.Test
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller


class SchemaMappingInspectorNullnessKotlinTests : SchemaMappingInspectorTestSupport() {


    @Test
    fun reportHasEntriesWhenSchemaFieldNonNullAndTypeFieldNullable() {
        val schema = """
						type Query {
							bookById(id: ID): NullableDataBook
						}
						type NullableDataBook {
							id: ID
							title: String!
						}
					
					""".trimIndent()
        val report: SchemaReport = inspectSchema(schema, NullableDataBookController::class.java)
        assertThatReport(report).containsFieldsNullnessErrors("NullableDataBook", "title")
    }

    @Test
    fun reportHasEntriesWhenSchemaFieldNonNullAndDataFetcherNullable() {
        val schema = """
						type Query {
							bookById(id: ID): Book!
						}
						type Book {
							id: ID
							title: String
						}
					
					""".trimIndent()
        val report: SchemaReport = inspectSchema(schema, NullableFetcherBookController::class.java)
        assertThatReport(report).containsFieldsNullnessErrors("Query", "bookById")
    }

    @Test
    fun reportIsEmptuWhenSchemaFieldNonNullAndDataFetcherArgumentNullable() {
        val schema = """
						type Query {
							bookById(id: ID!): Book
						}
						type Book {
							id: ID!
							title: String!
						}
					
					""".trimIndent()
        val report: SchemaReport = inspectSchema(schema, NullableFetcherArgumentBookController::class.java)
        assertThatReport(report).isEmpty();
    }


    @Controller
    class NullableDataBookController {
        @QueryMapping
        fun bookById(id: String): NullableDataBook {
            return NullableDataBook("42", null)
        }
    }

    class NullableDataBook(val id: String, val title: String?)

    @Controller
    class NullableFetcherArgumentBookController {
        @QueryMapping
        fun bookById(id: String?): Book? {
            return null
        }
    }

    @Controller
    class NullableFetcherBookController {
        @QueryMapping
        fun bookById(id: String): Book? {
            return null
        }
    }

    data class Book(val id: String, val title: String)

}
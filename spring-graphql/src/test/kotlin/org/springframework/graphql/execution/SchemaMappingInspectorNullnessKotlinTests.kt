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

package org.springframework.graphql.execution

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller


class SchemaMappingInspectorNullnessKotlinTests : SchemaMappingInspectorTestSupport() {


    @Test
    @Disabled("until https://github.com/spring-projects/spring-framework/issues/35419 is fixed")
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
    fun reportHasEntriesWhenSchemaFieldNonNullAndDataFetcherArgumentNullable() {
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
        assertThatReport(report).containsArgumentsNullnessErrors("java.lang.String id");
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
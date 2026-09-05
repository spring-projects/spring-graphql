/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.data.method.annotation.support

import graphql.ExecutionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.graphql.GraphQlSetup
import org.springframework.graphql.data.method.annotation.ProjectAs
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.stereotype.Controller
import reactor.test.StepVerifier

/**
 * Tests for projected coroutine controller return values.
 *
 * @author Goutam Adwant
 */
class SchemaMappingProjectionKotlinTests {

	@Test
	fun suspendingQuery() {
		AnnotationConfigApplicationContext(BookController::class.java).use { context ->
			val graphQl = GraphQlSetup.schemaContent("""
				type Query { book: Book }
				type Book { title: String }
				""").runtimeWiringForAnnotatedControllers(context).toGraphQl()
			val result = graphQl.execute("{ book { title } }")
			assertThat(result.errors).isEmpty()
			assertThat(result.getData<Map<String, Any>>()).containsEntry("book", mapOf("title" to "GraphQL"))
		}
	}

	@Test
	fun suspendingSubscription() {
		AnnotationConfigApplicationContext(BookController::class.java).use { context ->
			val graphQl = GraphQlSetup.schemaContent("""
				type Query { book: Book }
				type Subscription { updates: Book }
				type Book { title: String }
				""").runtimeWiringForAnnotatedControllers(context).toGraphQl()
			val result = graphQl.execute("subscription { updates { title } }")
			assertThat(result.errors).isEmpty()
			StepVerifier.create(requireNotNull(result.getData<Publisher<ExecutionResult>>()))
				.assertNext { event ->
					assertThat(event.errors).isEmpty()
					assertThat(event.getData<Map<String, Any>>()).containsEntry("updates", mapOf("title" to "GraphQL"))
				}
				.verifyComplete()
		}
	}

	@Controller
	class BookController {

		@QueryMapping
		@ProjectAs(BookProjection::class)
		suspend fun book(): Book {
			delay(1)
			return Book("GraphQL")
		}

		@SubscriptionMapping
		@ProjectAs(BookProjection::class)
		suspend fun updates(): Flow<Book> = flow { emit(book()) }
	}

	interface BookProjection {

		@Value("#{target.name}")
		fun getTitle(): String
	}

	data class Book(val name: String)
}

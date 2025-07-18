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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.graphql.*
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry
import org.springframework.stereotype.Controller
import reactor.test.StepVerifier
import java.util.function.Supplier

/**
 * Kotlin tests for GraphQL requests handled with {@code @SchemaMapping} methods.
 *
 * @author Rossen Stoyanchev
 */
class SchemaMappingInvocationKotlinTests {

	@Test
	fun queryWithScalarArgument() {
		val document = """
				{ bookById(id:"1") {id, name, author {firstName, lastName}}}
				"""

		val responseMono = graphQlService().execute(document)

		val book = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book::class.java)
		Assertions.assertThat(book.id).isEqualTo(1)
		Assertions.assertThat(book.name).isEqualTo("Nineteen Eighty-Four")

		val author = book.author
		Assertions.assertThat(author.firstName).isEqualTo("George")
		Assertions.assertThat(author.lastName).isEqualTo("Orwell")
	}

	@Test
	fun queryWithObjectArgument() {
		val document = """
				{ booksByCriteria(criteria: {author:"Orwell"}) {id, name}}	
				"""

		val responseMono = graphQlService().execute(document)

		val bookList = ResponseHelper.forResponse(responseMono).toList("booksByCriteria", Book::class.java)
		Assertions.assertThat(bookList).hasSize(2)
		Assertions.assertThat(bookList[0].name).isEqualTo("Nineteen Eighty-Four")
		Assertions.assertThat(bookList[1].name).isEqualTo("Animal Farm")
	}

	@Test
	fun subscription() {
		val document = """
				subscription {bookSearch(author:"Orwell") {id, name}} 
				"""

		val responseMono = graphQlService().execute(document)

		val bookFlux = ResponseHelper.forSubscription(responseMono)
			.map { response: ResponseHelper -> response.toEntity("bookSearch", Book::class.java) }

		StepVerifier.create(bookFlux)
			.consumeNextWith { book: Book ->
				Assertions.assertThat(book.id).isEqualTo(1)
				Assertions.assertThat(book.name).isEqualTo("Nineteen Eighty-Four")
			}
			.consumeNextWith { book: Book ->
				Assertions.assertThat(book.id).isEqualTo(5)
				Assertions.assertThat(book.name).isEqualTo("Animal Farm")
			}
			.verifyComplete()
	}

	private fun graphQlService(): TestExecutionGraphQlService {
		val registry: BatchLoaderRegistry = DefaultBatchLoaderRegistry()

		val context = AnnotationConfigApplicationContext()
		context.register(BookController::class.java)
		context.registerBean(BatchLoaderRegistry::class.java, Supplier { registry })
		context.refresh()

		val configurer = AnnotatedControllerConfigurer()
		configurer.setExecutor(SimpleAsyncTaskExecutor())
		configurer.setApplicationContext(context)
		configurer.afterPropertiesSet()

		val setup = GraphQlSetup.schemaResource(BookSource.schema).runtimeWiring(configurer)

		return setup.dataLoaders(registry).toGraphQlService()
	}


	@Controller
	class BookController {

		@QueryMapping
		suspend fun bookById(@Argument id: Long): Book {
			delay(50)
			return BookSource.getBookWithoutAuthor(id)
		}

		@QueryMapping
		fun booksByCriteria(@Argument criteria: BookCriteria): List<Book> {
			return BookSource.findBooksByAuthor(criteria.author)
		}

		@SubscriptionMapping
		suspend fun bookSearch(@Argument author : String): Flow<Book> {
			return flow {
				for (book in BookSource.findBooksByAuthor(author)) {
					delay(10)
					emit(BookSource.getBookWithoutAuthor(book.id))
				}
			}
		}

		@SchemaMapping
		suspend fun author(book: Book): Author {
			delay(50)
			return BookSource.getAuthor(book.authorId)
		}
	}

}
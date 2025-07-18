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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.graphql.*
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry
import org.springframework.stereotype.Controller
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors

/**
 * Kotlin tests for GraphQL requests handled with {@code @BatchMapping} methods.
 *
 * @author Rossen Stoyanchev
 */
class BatchMappingInvocationKotlinTests {

	companion object {

		@JvmStatic
		fun argumentSource() = listOf(
			CoroutineBatchController::class.java,
			FlowBatchController::class.java
		)
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	fun queryWithObjectArgument(controllerClass: Class<*>) {
		val document = """
				{ booksByCriteria(criteria: {author:"Orwell"}) {id, name, author {firstName, lastName}}}	
				"""

		val responseMono = graphQlService(controllerClass).execute(document)

		val bookList = ResponseHelper.forResponse(responseMono).toList("booksByCriteria", Book::class.java)
		assertThat(bookList).hasSize(2)

		assertThat(bookList[0].name).isEqualTo("Nineteen Eighty-Four")
		assertThat(bookList[0].author.firstName).isEqualTo("George")
		assertThat(bookList[0].author.lastName).isEqualTo("Orwell")

		assertThat(bookList[1].name).isEqualTo("Animal Farm")
		assertThat(bookList[1].author.firstName).isEqualTo("George")
		assertThat(bookList[1].author.lastName).isEqualTo("Orwell")
	}


	private fun graphQlService(controllerClass: Class<*>): TestExecutionGraphQlService {
		val registry: BatchLoaderRegistry = DefaultBatchLoaderRegistry()

		val context = AnnotationConfigApplicationContext()
		context.register(controllerClass)
		context.registerBean(BatchLoaderRegistry::class.java, Supplier { registry })
		context.refresh()

		val configurer = AnnotatedControllerConfigurer()
		configurer.setExecutor(SimpleAsyncTaskExecutor())
		configurer.setApplicationContext(context)
		configurer.afterPropertiesSet()

		val setup = GraphQlSetup.schemaResource(BookSource.schema).runtimeWiring(configurer)

		return setup.dataLoaders(registry).toGraphQlService()
	}


	open class BookController {
		@QueryMapping
		fun booksByCriteria(@Argument criteria: BookCriteria): List<Book> {
			return BookSource.findBooksByAuthor(criteria.author).stream()
				.map { BookSource.getBookWithoutAuthor(it.id) }
				.toList()
		}
	}

	@Controller
	class CoroutineBatchController : BookController() {

		@BatchMapping
		suspend fun author(books: List<Book>): Map<Book, Author> {
			delay(100)
			return books.stream().collect(
				Collectors.toMap(Function.identity()) { b: Book -> BookSource.getAuthor(b.authorId) }
			)
		}
	}


	@Controller
	class FlowBatchController : BookController() {

		@BatchMapping
		suspend fun author(books: List<Book>): Flow<Author> {
			return flow {
				delay(100)
				for (book in books) {
					emit(BookSource.getAuthor(book.getAuthorId()))
				}
			}
		}
	}

}
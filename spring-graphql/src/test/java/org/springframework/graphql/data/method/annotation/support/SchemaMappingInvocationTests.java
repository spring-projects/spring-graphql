/*
 * Copyright 2002-2022 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.web.ProjectedPayload;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookCriteria;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test GraphQL requests handled through {@code @SchemaMapping} methods.
 *
 * @author Rossen Stoyanchev
 * @author Mark Paluch
 */
public class SchemaMappingInvocationTests {

	@Test
	void queryWithScalarArgument() {
		String document = "{ " +
				"  bookById(id:\"1\") { " +
				"    id" +
				"    name" +
				"    author {" +
				"      firstName" +
				"      lastName" +
				"    }" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(TestExecutionRequest.forDocument(document));

		Book book = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(book.getId()).isEqualTo(1);
		assertThat(book.getName()).isEqualTo("Nineteen Eighty-Four");

		Author author = book.getAuthor();
		assertThat(author.getFirstName()).isEqualTo("George");
		assertThat(author.getLastName()).isEqualTo("Orwell");
	}

	@Test
	void queryWithObjectArgument() {
		String document = "{ " +
				"  booksByCriteria(criteria: {author:\"Orwell\"}) { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(TestExecutionRequest.forDocument(document));

		List<Book> bookList = ResponseHelper.forResponse(responseMono).toList("booksByCriteria", Book.class);
		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).getName()).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).getName()).isEqualTo("Animal Farm");
	}

	@Test
	void queryWithProjectionOnArgumentsMap() {
		String document = "{ " +
				"  booksByProjectedArguments(author:\"Orwell\") { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(TestExecutionRequest.forDocument(document));

		List<Book> bookList = ResponseHelper.forResponse(responseMono).toList("booksByProjectedArguments", Book.class);
		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).getName()).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).getName()).isEqualTo("Animal Farm");
	}

	@Test
	void queryWithProjectionOnNamedArgument() {
		String document = "{ " +
				"  booksByProjectedCriteria(criteria: {author:\"Orwell\"}) { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(TestExecutionRequest.forDocument(document));

		List<Book> bookList = ResponseHelper.forResponse(responseMono).toList("booksByProjectedCriteria", Book.class);
		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).getName()).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).getName()).isEqualTo("Animal Farm");
	}

	@Test
	void queryWithArgumentViaDataFetchingEnvironment() {
		String document = "{ " +
				"  authorById(id:\"101\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		AtomicReference<GraphQLContext> contextRef = new AtomicReference<>();
		ExecutionGraphQlRequest request = TestExecutionRequest.forDocument(document);
		request.configureExecutionInput((executionInput, builder) -> {
			contextRef.set(executionInput.getGraphQLContext());
			return executionInput;
		});

		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(request);

		Author author = ResponseHelper.forResponse(responseMono).toEntity("authorById", Author.class);
		assertThat(author.getId()).isEqualTo(101);
		assertThat(author.getFirstName()).isEqualTo("George");
		assertThat(author.getLastName()).isEqualTo("Orwell");

		assertThat(contextRef.get().<String>get("key")).isEqualTo("value");
	}

	@Test
	void mutation() {
		String document = "mutation { " +
				"  addAuthor(firstName:\"James\", lastName:\"Joyce\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(TestExecutionRequest.forDocument(document));

		Author author = ResponseHelper.forResponse(responseMono).toEntity("addAuthor", Author.class);
		assertThat(author.getId()).isEqualTo(99);
		assertThat(author.getFirstName()).isEqualTo("James");
		assertThat(author.getLastName()).isEqualTo("Joyce");
	}

	@Test
	void subscription() {
		String document = "subscription { " +
				"  bookSearch(author:\"Orwell\") { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		Mono<ExecutionGraphQlResponse> responseMono = graphQlService().execute(TestExecutionRequest.forDocument(document));

		Flux<Book> bookFlux = ResponseHelper.forSubscription(responseMono)
				.map(response -> response.toEntity("bookSearch", Book.class));

		StepVerifier.create(bookFlux)
				.consumeNextWith(book -> {
					assertThat(book.getId()).isEqualTo(1);
					assertThat(book.getName()).isEqualTo("Nineteen Eighty-Four");
				})
				.consumeNextWith(book -> {
					assertThat(book.getId()).isEqualTo(5);
					assertThat(book.getName()).isEqualTo("Animal Farm");
				})
				.verifyComplete();
	}


	private ExecutionGraphQlService graphQlService() {
		BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(BookController.class);
		context.registerBean(BatchLoaderRegistry.class, () -> registry);
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setExecutor(new SimpleAsyncTaskExecutor());
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		return GraphQlSetup.schemaResource(BookSource.schema)
				.runtimeWiring(configurer)
				.dataLoaders(registry)
				.toGraphQlService();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BookController {

		public BookController(BatchLoaderRegistry batchLoaderRegistry) {
			batchLoaderRegistry.forTypePair(Long.class, Author.class)
					.registerBatchLoader((ids, env) -> Flux.fromIterable(ids).map(BookSource::getAuthor));
		}

		@QueryMapping
		public Book bookById(@Argument Long id) {
			return BookSource.getBookWithoutAuthor(id);
		}

		@QueryMapping
		public List<Book> booksByCriteria(@Argument BookCriteria criteria) {
			return BookSource.findBooksByAuthor(criteria.getAuthor());
		}

		@QueryMapping
		public List<Book> booksByProjectedArguments(BookProjection projection) {
			return BookSource.findBooksByAuthor(projection.getAuthor());
		}

		@QueryMapping
		public List<Book> booksByProjectedCriteria(@Argument BookProjection criteria) {
			return BookSource.findBooksByAuthor(criteria.getAuthor());
		}

		@SchemaMapping
		public CompletableFuture<Author> author(Book book, DataLoader<Long, Author> dataLoader) {
			return dataLoader.load(book.getAuthorId());
		}

		@QueryMapping
		public Callable<Author> authorById(DataFetchingEnvironment environment, GraphQLContext context) {
			return () -> {
				context.put("key", "value");
				String id = environment.getArgument("id");
				return BookSource.getAuthor(Long.parseLong(id));
			};
		}

		@MutationMapping
		public Author addAuthor(@Argument String firstName, @Argument String lastName) {
			return new Author(99L, firstName, lastName);
		}

		@SubscriptionMapping
		public Flux<Book> bookSearch(@Argument String author) {
			return Flux.fromIterable(BookSource.findBooksByAuthor(author));
		}
	}

	@ProjectedPayload
	interface BookProjection {

		String getAuthor();

	}

}

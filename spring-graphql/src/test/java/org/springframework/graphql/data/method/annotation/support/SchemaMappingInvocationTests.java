/*
 * Copyright 2002-2021 the original author or authors.
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookCriteria;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.GraphQlTestUtils;
import org.springframework.graphql.RequestInput;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test GraphQL requests handled through {@code @SchemaMapping} methods.
 *
 * @author Rossen Stoyanchev
 */
public class SchemaMappingInvocationTests {

	@Test
	void queryWithScalarArgument() {
		String query = "{ " +
				"  bookById(id:\"1\") { " +
				"    id" +
				"    name" +
				"    author {" +
				"      firstName" +
				"      lastName" +
				"    }" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQlService()
				.execute(new RequestInput(query, null, null))
				.block();

		Map<String, Object> book = GraphQlTestUtils.checkErrorsAndGetData(result, "bookById");
		assertThat(book.get("id")).isEqualTo("1");
		assertThat(book.get("name")).isEqualTo("Nineteen Eighty-Four");

		Map<String, Object> author = (Map<String, Object>) book.get("author");
		assertThat(author.get("firstName")).isEqualTo("George");
		assertThat(author.get("lastName")).isEqualTo("Orwell");
	}

	@Test
	void queryWithObjectArgument() {
		String query = "{ " +
				"  booksByCriteria(criteria: {author:\"Orwell\"}) { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQlService()
				.execute(new RequestInput(query, null, null))
				.block();

		List<Map<String, Object>> bookList = GraphQlTestUtils.checkErrorsAndGetData(result, "booksByCriteria");

		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).get("name")).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).get("name")).isEqualTo("Animal Farm");
	}

	@Test
	void queryWithArgumentViaDataFetchingEnvironment() {
		String query = "{ " +
				"  authorById(id:\"101\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		AtomicReference<GraphQLContext> contextRef = new AtomicReference<>();
		RequestInput requestInput = new RequestInput(query, null, null);
		requestInput.configureExecutionInput((executionInput, builder) -> {
			contextRef.set(executionInput.getGraphQLContext());
			return executionInput;
		});

		ExecutionResult result = initGraphQlService()
				.execute(requestInput)
				.block();

		Map<String, Object> author = GraphQlTestUtils.checkErrorsAndGetData(result, "authorById");

		assertThat(author.get("id")).isEqualTo("101");
		assertThat(author.get("firstName")).isEqualTo("George");
		assertThat(author.get("lastName")).isEqualTo("Orwell");

		assertThat(contextRef.get().<String>get("key")).isEqualTo("value");
	}

	@Test
	void mutation() {
		String operation = "mutation { " +
				"  addAuthor(firstName:\"James\", lastName:\"Joyce\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQlService()
				.execute(new RequestInput(operation, null, null))
				.block();

		Map<String, Object> author = GraphQlTestUtils.checkErrorsAndGetData(result, "addAuthor");
		assertThat(author.get("id")).isEqualTo("99");
		assertThat(author.get("firstName")).isEqualTo("James");
		assertThat(author.get("lastName")).isEqualTo("Joyce");
	}

	@Test
	void subscription() {
		String operation = "subscription { " +
				"  bookSearch(author:\"Orwell\") { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQlService()
				.execute(new RequestInput(operation, null, null))
				.block();

		Publisher<ExecutionResult> publisher = GraphQlTestUtils.checkErrorsAndGetData(result);

		Flux<Map<String, Object>> bookFlux = Flux.from(publisher).map(rs -> {
			Map<String, Object> map = rs.getData();
			return (Map<String, Object>) map.get("bookSearch");
		});

		StepVerifier.create(bookFlux)
				.consumeNextWith(book -> {
					assertThat(book.get("id")).isEqualTo("1");
					assertThat(book.get("name")).isEqualTo("Nineteen Eighty-Four");
				})
				.consumeNextWith(book -> {
					assertThat(book.get("id")).isEqualTo("5");
					assertThat(book.get("name")).isEqualTo("Animal Farm");
				})
				.verifyComplete();
	}


	private ExecutionGraphQlService initGraphQlService() {
		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
		applicationContext.register(TestConfig.class);
		applicationContext.refresh();

		return applicationContext.getBean(ExecutionGraphQlService.class);
	}


	@Configuration
	static class TestConfig {

		@Bean
		public BookController bookController() {
			return new BookController(batchLoaderRegistry());
		}

		@Bean
		public GraphQlService graphQlService(GraphQlSource graphQlSource) {
			ExecutionGraphQlService service = new ExecutionGraphQlService(graphQlSource);
			service.addDataLoaderRegistrar(batchLoaderRegistry());
			return service;
		}

		@Bean
		public GraphQlSource graphQlSource() {
			return GraphQlSource.builder()
					.schemaResources(new ClassPathResource("books/schema.graphqls"))
					.configureRuntimeWiring(annotatedDataFetcherConfigurer())
					.build();
		}

		@Bean
		public AnnotatedControllerConfigurer annotatedDataFetcherConfigurer() {
			return new AnnotatedControllerConfigurer();
		}

		@Bean
		public BatchLoaderRegistry batchLoaderRegistry() {
			return new DefaultBatchLoaderRegistry();
		}

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

		@SchemaMapping
		public CompletableFuture<Author> author(Book book, DataLoader<Long, Author> dataLoader) {
			return dataLoader.load(book.getAuthorId());
		}

		@QueryMapping
		public Author authorById(DataFetchingEnvironment environment, GraphQLContext context) {
			context.put("key", "value");
			String id = environment.getArgument("id");
			return BookSource.getAuthor(Long.parseLong(id));
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

}

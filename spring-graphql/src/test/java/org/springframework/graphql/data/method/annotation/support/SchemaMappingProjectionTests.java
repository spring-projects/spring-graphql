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

package org.springframework.graphql.data.method.annotation.support;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.FieldCoordinates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.ProjectAs;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for projected controller return values.
 *
 * @author Goutam Adwant
 */
class SchemaMappingProjectionTests {

	private final AnnotationConfigApplicationContext context =
			new AnnotationConfigApplicationContext(BookController.class);

	@AfterEach
	void closeContext() {
		this.context.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"book", "mono", "future", "result"})
	void scalarReturnValue(String field) {
		ExecutionResult result = graphQl(field, "Book").execute("{ " + field + " { title } }");
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.<Map<String, Object>>getData()).containsEntry(field, Map.of("title", "GraphQL"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"books", "array", "flux", "monoList", "bookFuture"})
	void collectionReturnValue(String field) {
		ExecutionResult result = graphQl(field, "[Book]").execute("{ " + field + " { title } }");
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.<Map<String, Object>>getData()).containsEntry(field, List.of(Map.of("title", "GraphQL")));
	}

	@Test
	void nullCollectionElement() {
		ExecutionResult result = graphQl("nullableBooks", "[Book]").execute("{ nullableBooks { title } }");
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.<Map<String, Object>>getData()).containsEntry("nullableBooks",
				Arrays.asList(Map.of("title", "GraphQL"), null));
	}

	@ParameterizedTest
	@ValueSource(strings = {"nullBook", "emptyMono"})
	void emptyReturnValue(String field) {
		ExecutionResult result = graphQl(field, "Book").execute("{ " + field + " { title } }");
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.<Map<String, Object>>getData()).containsEntry(field, null);
	}

	@ParameterizedTest
	@ValueSource(strings = {"failure", "monoFailure"})
	void exceptionHandling(String field) {
		ExecutionResult result = graphQl(field, "Book").execute("{ " + field + " { title } }");
		assertThat(result.getErrors()).singleElement().extracting(GraphQLError::getMessage).isEqualTo("Expected failure");
	}

	@Test
	void dataFetcherResultMetadataAndProjectedSource() {
		ExecutionResult result = graphQl("result", "Book").execute("{ result { title description } }");
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.<Map<String, Object>>getData()).containsEntry("result",
				Map.of("title", "GraphQL", "description", "GraphQL"));
		assertThat(result.getExtensions()).containsEntry("test", "value");
	}

	@Test
	void callableReturnValue() throws Exception {
		try (SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor()) {
			AnnotatedControllerConfigurer configurer = configurer();
			configurer.setExecutor(executor);
			ExecutionResult result = setup("callable", "Book").runtimeWiring(configurer).toGraphQl()
					.execute("{ callable { title } }");
			assertThat(result.getErrors()).isEmpty();
			assertThat(result.<Map<String, Object>>getData()).containsEntry("callable", Map.of("title", "GraphQL"));
		}
	}

	@Test
	void subscriptionReturnValue() {
		GraphQL graphQl = GraphQlSetup.schemaContent("""
				type Query { book: Book }
				type Subscription { updates: Book }
				type Book { title: String }
				""").runtimeWiring(configurer()).toGraphQl();
		ExecutionResult result = graphQl.execute("subscription { updates { title } }");
		assertThat(result.getErrors()).isEmpty();
		StepVerifier.create(result.<Publisher<ExecutionResult>>getData(), 1)
				.assertNext((event) -> {
					assertThat(event.getErrors()).isEmpty();
					assertThat(event.<Map<String, Object>>getData()).containsEntry("updates", Map.of("title", "GraphQL"));
				})
				.thenCancel()
				.verify();
	}

	@ParameterizedTest
	@ValueSource(strings = {"monoList", "bookFuture"})
	void describesProjectionReturnType(String field) {
		GraphQL graphQl = graphQl(field, "[Book]");
		SelfDescribingDataFetcher<?> fetcher = (SelfDescribingDataFetcher<?>) graphQl.getGraphQLSchema()
				.getCodeRegistry().getDataFetcher(FieldCoordinates.coordinates("Query", field),
						graphQl.getGraphQLSchema().getQueryType().getFieldDefinition(field));
		assertThat(fetcher.getReturnType().getGeneric(0, 0).resolve()).isEqualTo(BookProjection.class);
	}

	@ParameterizedTest
	@ValueSource(classes = {int.class, List.class, Map.class})
	void rejectsInvalidProjectionTargets(Class<?> type) {
		assertThatIllegalArgumentException().isThrownBy(() -> new ReturnValueProjector(type, this.context));
	}

	@Test
	void projectionTypeMustBeAnInterface() {
		try (AnnotationConfigApplicationContext invalid = new AnnotationConfigApplicationContext(InvalidController.class)) {
			assertThatIllegalArgumentException().isThrownBy(() -> setup("book", "Book")
					.runtimeWiringForAnnotatedControllers(invalid).toGraphQl())
					.withMessage("@ProjectAs requires an interface projection type");
		}
	}

	private GraphQL graphQl(String field, String type) {
		return setup(field, type).runtimeWiring(configurer()).toGraphQl();
	}

	private GraphQlSetup setup(String field, String type) {
		return GraphQlSetup.schemaContent("type Query { " + field + ": " + type + " } " +
				"type Book { title: String, description: String }");
	}

	private AnnotatedControllerConfigurer configurer() {
		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(this.context);
		configurer.afterPropertiesSet();
		return configurer;
	}

	@Controller
	static class BookController {

		@QueryMapping
		@BookView
		public Book book() {
			return new Book("GraphQL");
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Mono<Book> mono() {
			return Mono.just(book());
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public CompletableFuture<Book> future() {
			return CompletableFuture.completedFuture(book());
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public DataFetcherResult<Book> result() {
			return DataFetcherResult.<Book>newResult().data(book()).localContext("context")
					.extensions(Map.of("test", "value")).build();
		}

		@SchemaMapping(typeName = "Book")
		public String description(BookProjection book) {
			return book.getTitle();
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Callable<Book> callable() {
			return this::book;
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public List<Book> books() {
			return List.of(book());
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Book[] array() {
			return new Book[] {book()};
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Flux<Book> flux() {
			return Flux.just(book());
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Mono<List<Book>> monoList() {
			return Mono.just(books());
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public BookFuture bookFuture() {
			BookFuture future = new BookFuture();
			future.complete(books());
			return future;
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public List<Book> nullableBooks() {
			return Arrays.asList(book(), null);
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Book nullBook() {
			return null;
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Mono<Book> emptyMono() {
			return Mono.empty();
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Book failure() {
			throw new IllegalStateException("Expected failure");
		}

		@QueryMapping
		@ProjectAs(BookProjection.class)
		public Mono<Book> monoFailure() {
			return Mono.error(new IllegalStateException("Expected failure"));
		}

		@GraphQlExceptionHandler
		public GraphQLError handle(IllegalStateException exception) {
			return GraphqlErrorBuilder.newError().message(exception.getMessage()).build();
		}

		@SubscriptionMapping
		@ProjectAs(BookProjection.class)
		public Flux<Book> updates() {
			return Flux.concat(Flux.just(book()), Flux.never());
		}
	}

	@Controller
	static class InvalidController {

		@QueryMapping
		@ProjectAs(String.class)
		public Book book() {
			return null;
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@ProjectAs(BookProjection.class)
	@interface BookView {
	}

	interface BookProjection {

		@Value("#{target.name}")
		String getTitle();
	}

	static class BookFuture extends CompletableFuture<List<Book>> {
	}

	record Book(String name) {
	}

}

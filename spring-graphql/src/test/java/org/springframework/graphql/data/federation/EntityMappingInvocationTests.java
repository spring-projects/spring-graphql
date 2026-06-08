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

package org.springframework.graphql.data.federation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.Library;
import org.springframework.graphql.LibraryId;
import org.springframework.graphql.Location;
import org.springframework.graphql.LocationArea;
import org.springframework.graphql.LocationAreaId;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionGraphQlService;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for requests handled through {@code @EntityMapping} methods.
 *
 * @author Rossen Stoyanchev
 */
class EntityMappingInvocationTests {

	private static final Resource bookFederationSchema = new ClassPathResource("books/federation-schema.graphqls");

	private static final String bookDocument = """
			query Entities($representations: [_Any!]!) {
				_entities(representations: $representations) {
					...on Book {
						id
						author {
							id
							firstName
							lastName
						}
					}
				}
			}
			""";

	private static final Resource libraryFederationSchema = new ClassPathResource("library/federation-schema.graphqls");

	private static final String locationAreaDocument = """
			query Entities($representations: [_Any!]!) {
				_entities(representations: $representations) {
					...on LocationArea {
						location {
							id
						}
					}
				}
			}
			""";

	private static final String libraryDocument = """
			query Entities($representations: [_Any!]!) {
				_entities(representations: $representations) {
					...on Library {
						id
						location {
							id
						}
					}
				}
			}
			""";

	@Test
	void fetchEntities() {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("__typename", "Book", "id", "3"),
						Map.of("__typename", "Book", "id", "5"),
						Map.of("__typename", "PrintedMedia", "id", "42")));

		ResponseHelper helper = executeWith(BookController.class, bookFederationSchema, bookDocument, variables);

		assertAuthor(0, "Joseph", "Heller", helper);
		assertAuthor(1, "George", "Orwell", helper);
		assertAuthor(2, "Douglas", "Adams", helper);
	}

	@Test
	void fetchEntitiesWithExceptions() {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("id", "-95"),  // RepresentationException, no "__typename"
						Map.of("__typename", "Unknown"),  // RepresentationException, no fetcher
						Map.of("__typename", "Book", "id", "-97"),  // IllegalArgumentException
						Map.of("__typename", "Book", "id", "-98"),  // IllegalStateException
						Map.of("__typename", "Book", "id", "-99"),  // null
						Map.of("__typename", "Book", "id", "3"),
						Map.of("__typename", "Book", "id", "5")));

		ResponseHelper helper = executeWith(BookController.class, bookFederationSchema, bookDocument, variables);

		assertError(helper, 0, "BAD_REQUEST", "Missing \"__typename\" argument");
		assertError(helper, 1, "INTERNAL_ERROR", "No entity fetcher");
		assertError(helper, 2, "BAD_REQUEST", "handled");
		assertError(helper, 3, "INTERNAL_ERROR", "not handled");
		assertError(helper, 4, "INTERNAL_ERROR", "Entity fetcher returned null or completed empty");

		assertAuthor(5, "Joseph", "Heller", helper);
		assertAuthor(6, "George", "Orwell", helper);
	}

	@Test // gh-1057
	void fetchEntitiesWithEmptyList() {
		Map<String, Object> vars = Map.of("representations", Collections.emptyList());
		ResponseHelper helper = executeWith(BookController.class, bookFederationSchema, bookDocument, vars);

		assertThat(helper.toEntity("_entities.length()", Integer.class)).isEqualTo(0);
	}

	@Test
	void fetchSingleNestedKeyEntity() {
		Map<String, Object> variables = Map.of("representations", List.of(
			Map.of("__typename", "LocationArea", "location", Map.of("id", "1"))
		));

		ResponseHelper helper = executeWith(LibraryController.class, libraryFederationSchema, locationAreaDocument, variables);

		LocationArea locationArea = helper.toEntity("_entities[0]", LocationArea.class);
		assertThat(locationArea.location().id()).isEqualTo("1");
	}

	@Test
	void fetchMixedNestedKeyEntity() {
		Map<String, Object> variables = Map.of("representations", List.of(
				Map.of("__typename", "Library", "id", "1", "location", Map.of("id", "1"))
		));

		ResponseHelper helper = executeWith(LibraryController.class, libraryFederationSchema, libraryDocument, variables);

		Library library = helper.toEntity("_entities[0]", Library.class);
		assertThat(library.id()).isEqualTo("1");
		assertThat(library.location().id()).isEqualTo("1");
	}

	@ValueSource(classes = {BookListController.class, BookFluxController.class})
	@ParameterizedTest
	void batching(Class<?> controllerClass) {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("__typename", "Book", "id", "1"),
						Map.of("__typename", "Book", "id", "4"),
						Map.of("__typename", "Book", "id", "5"),
						Map.of("__typename", "Book", "id", "42"),
						Map.of("__typename", "Book", "id", "53")));

		ResponseHelper helper = executeWith(controllerClass, bookFederationSchema, bookDocument, variables);

		assertAuthor(0, "George", "Orwell", helper);
		assertAuthor(1, "Virginia", "Woolf", helper);
		assertAuthor(2, "George", "Orwell", helper);
		assertAuthor(3, "Douglas", "Adams", helper);
		assertAuthor(4, "Vince", "Gilligan", helper);
	}

	@ValueSource(classes = {BookListController.class, BookFluxController.class})
	@ParameterizedTest
	void batchingWithError(Class<?> controllerClass) {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("__typename", "Book", "id", "-97"),
						Map.of("__typename", "Book", "id", "4"),
						Map.of("__typename", "Book", "id", "5")));

		ResponseHelper helper = executeWith(controllerClass, bookFederationSchema, bookDocument, variables);

		assertError(helper, 0, "BAD_REQUEST", "handled");
		assertError(helper, 1, "BAD_REQUEST", "handled");
		assertError(helper, 2, "BAD_REQUEST", "handled");
	}

	@ValueSource(classes = {BookListController.class, BookFluxController.class})
	@ParameterizedTest
	void batchingWithoutResult(Class<?> controllerClass) {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("__typename", "Book", "id", "-99"),
						Map.of("__typename", "Book", "id", "4"),
						Map.of("__typename", "Book", "id", "5")));

		ResponseHelper helper = executeWith(controllerClass, bookFederationSchema, bookDocument, variables);

		assertError(helper, 0, "INTERNAL_ERROR", "Entity fetcher returned null or completed empty");
		assertError(helper, 1, "INTERNAL_ERROR", "Entity fetcher returned null or completed empty");
		assertError(helper, 2, "INTERNAL_ERROR", "Entity fetcher returned null or completed empty");
	}

	@Test
	void dataLoader() {
		Map<String, Object> variables =
				Map.of("representations", List.of(
						Map.of("__typename", "Book", "id", "3"),
						Map.of("__typename", "Book", "id", "5")));

		ResponseHelper helper = executeWith(DataLoaderBookController.class, bookFederationSchema, bookDocument, variables);

		assertAuthor(0, "Joseph", "Heller", helper);
		assertAuthor(1, "George", "Orwell", helper);
	}

	@Test
	void unmappedEntity() {
		assertThatIllegalStateException().isThrownBy(() -> executeWith(EmptyController.class, bookFederationSchema, bookDocument, Map.of()))
				.withMessage("Unmapped entity types: 'Media', 'PrintedMedia', 'Book'");
	}

	private static ResponseHelper executeWith(Class<?> controllerClass, Resource federationSchema, String document,
											  Map<String, Object> variables) {

		ExecutionGraphQlRequest request = TestExecutionRequest.forDocumentAndVars(document, variables);
		Mono<ExecutionGraphQlResponse> responseMono = graphQlService(controllerClass, federationSchema).execute(request);
		return ResponseHelper.forResponse(responseMono);
	}

	private static void assertAuthor(int index, String firstName, String lastName, ResponseHelper helper) {
		Author author = helper.toEntity("_entities[" + index + "].author", Author.class);
		assertThat(author.getFirstName()).isEqualTo(firstName);
		assertThat(author.getLastName()).isEqualTo(lastName);
	}

	private static void assertError(ResponseHelper helper, int i, String errorType, String msg) {
		String path = "_entities[" + i + "]";
		assertThat(helper.error(i).message()).isEqualTo(msg);
		assertThat(helper.error(i).errorType()).isEqualTo(errorType);
		assertThat(helper.error(i).path()).isEqualTo("/" + path);
		assertThat(helper.<Object>rawValue(path)).isNull();
	}

	private static TestExecutionGraphQlService graphQlService(Class<?> controllerClass, Resource federationSchema) {
		BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(controllerClass);
		context.registerBean(BatchLoaderRegistry.class, () -> registry);
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		FederationSchemaFactory schemaFactory = new FederationSchemaFactory();
		schemaFactory.setApplicationContext(context);
		schemaFactory.afterPropertiesSet();

		return GraphQlSetup.schemaResource(federationSchema)
				.runtimeWiring(configurer)
				.schemaFactory(schemaFactory::createGraphQLSchema)
				.dataLoaders(registry)
				.toGraphQlService();
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BookController {

		@EntityMapping("Media")
		public @Nullable Book book(@Argument int id, Map<String, Object> map) {

			assertThat(map).hasSize(2)
					.containsKey("__typename")
					.containsEntry("id", String.valueOf(id));

			assertThat(map.get("__typename")).satisfies(s ->
					assertThat(s.equals("Book") || s.equals("PrintedMedia")).isTrue());

			return switch (id) {
				case -97 -> throw new IllegalArgumentException("handled");
				case -98 -> throw new IllegalStateException("not handled");
				case -99 -> null;
				default -> new Book((long) id, null, (Long) null);
			};
		}

		@BatchMapping
		public Flux<Author> author(List<Book> books) {
			return Flux.fromIterable(books).map(book -> BookSource.getBook(book.getId()).getAuthor());
		}

		@GraphQlExceptionHandler
		public GraphQLError handle(IllegalArgumentException ex, DataFetchingEnvironment env) {
			return GraphqlErrorBuilder.newError(env)
					.errorType(ErrorType.BAD_REQUEST)
					.message(ex.getMessage())
					.build();
		}
	}

	@SuppressWarnings("unused")
	@Controller
	private static class BookListController {

		private final BookBatchService batchService = new BookBatchService();

		@EntityMapping("Media")
		public List<Book> book(@Argument List<Integer> idList, List<Map<String, Object>> representations) {
			return this.batchService.book(idList, representations);
		}

		@BatchMapping
		public List<Author> author(List<Book> books) {
			return this.batchService.author(books);
		}

		@GraphQlExceptionHandler
		public GraphQLError handle(IllegalArgumentException ex, DataFetchingEnvironment env) {
			return this.batchService.handle(ex, env);
		}
	}


	@SuppressWarnings("unused")
	@Controller
	private static class BookFluxController {

		private final BookBatchService batchService = new BookBatchService();

		@EntityMapping("Media")
		public Flux<Book> book(@Argument List<Integer> idList, List<Map<String, Object>> representations) {
			return Flux.fromIterable(this.batchService.book(idList, representations));
		}

		@BatchMapping
		public Flux<Author> author(List<Book> books) {
			return Flux.fromIterable(this.batchService.author(books));
		}

		@GraphQlExceptionHandler
		public GraphQLError handle(IllegalArgumentException ex, DataFetchingEnvironment env) {
			return this.batchService.handle(ex, env);
		}
	}


	@SuppressWarnings("unused")
	@Controller
	private static class DataLoaderBookController {

		@Autowired
		public DataLoaderBookController(BatchLoaderRegistry batchLoaderRegistry) {
			batchLoaderRegistry.forTypePair(Integer.class, Book.class)
					.registerBatchLoader((ids, env) ->
							Flux.fromIterable(ids).map(id -> new Book((long) id, null, (Long) null)));
		}

		@Nullable
		@EntityMapping("Media")
		public Future<Book> book(@Argument int id, DataLoader<Integer, Book> dataLoader) {
			return dataLoader.load(id);
		}

		@BatchMapping
		public Flux<Author> author(List<Book> books) {
			return Flux.fromIterable(books).map(book -> BookSource.getBook(book.getId()).getAuthor());
		}
	}


	@SuppressWarnings("unused")
	@Controller
	private static class EmptyController {

	}


	private static class BookBatchService {

		public List<Book> book(List<Integer> idList, List<Map<String, Object>> representations) {

			if (idList.get(0) == -97) {
				throw new IllegalArgumentException("handled");
			}

			if (idList.get(0) == -99) {
				return Collections.emptyList();
			}

			assertThat(representations).hasSize(5).containsExactly(
					Map.of("__typename", "Book", "id", "1"),
					Map.of("__typename", "Book", "id", "4"),
					Map.of("__typename", "Book", "id", "5"),
					Map.of("__typename", "Book", "id", "42"),
					Map.of("__typename", "Book", "id", "53"));

			return idList.stream().map(id -> new Book((long) id, null, (Long) null)).toList();
		}

		public List<Author> author(List<Book> books) {
			return books.stream().map(book -> BookSource.getBook(book.getId()).getAuthor()).toList();
		}

		public GraphQLError handle(IllegalArgumentException ex, DataFetchingEnvironment env) {
			return GraphqlErrorBuilder.newError(env)
					.errorType(ErrorType.BAD_REQUEST)
					.message(ex.getMessage())
					.build();
		}
	}

	@SuppressWarnings("unused")
	@Controller
	public static class LibraryController {

		@EntityMapping
		public @Nullable Library library(@Argument LibraryId id, Map<String, Object> map) {

			assertThat(id.id()).isNotNull();
			assertThat(id.location().id()).isNotNull();

			assertThat(map).hasSize(3)
					.containsEntry("__typename", "Library")
					.containsEntry("id", "1")
					.containsEntry("location", Map.of("id", "1"));

			return new Library("1", new Location("1"));
		}

		@EntityMapping
		public @Nullable LocationArea locationArea(@Argument LocationAreaId id, Map<String, Object> map) {

			assertThat(id.location().id()).isNotNull();

			assertThat(map).hasSize(2)
					.containsEntry("__typename", "LocationArea")
					.containsEntry("location", Map.of("id", "1"));

			return new LocationArea(new Location("1"));
		}

	}

}

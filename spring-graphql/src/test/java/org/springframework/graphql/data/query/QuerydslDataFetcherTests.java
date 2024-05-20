/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.data.query;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.querydsl.core.types.Predicate;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.keyvalue.core.KeyValueTemplate;
import org.springframework.data.keyvalue.repository.support.KeyValueRepositoryFactory;
import org.springframework.data.map.MapKeyValueAdapter;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.graphql.Author;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.graphql.data.query.QuerydslDataFetcher.Builder;
import org.springframework.graphql.data.query.QuerydslDataFetcher.QuerydslBuilderCustomizer;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link QuerydslDataFetcher}.
 */
class QuerydslDataFetcherTests {

	private final KeyValueRepositoryFactory repositoryFactory =
			new KeyValueRepositoryFactory(new KeyValueTemplate(new MapKeyValueAdapter()));

	private final MockRepository mockRepository = repositoryFactory.getRepository(MockRepository.class);


	@Test
	void shouldFetchSingleItem() {
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		mockRepository.save(book);

		Consumer<GraphQlSetup> tester = setup -> {
			WebGraphQlRequest request = request("{ bookById(id: 42) {name}}");
			Mono<WebGraphQlResponse> responseMono = setup.toWebGraphQlHandler().handleRequest(request);
			Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);

			assertThat(actualBook.getName()).isEqualTo(book.getName());
		};

		// explicit wiring
		tester.accept(graphQlSetup("bookById", QuerydslDataFetcher.builder(mockRepository).single()));

		// auto registration
		tester.accept(graphQlSetup(mockRepository));
	}

	@Test
	void shouldFetchMultipleItems() {
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg"));
		mockRepository.saveAll(Arrays.asList(book1, book2));

		Consumer<GraphQlSetup> tester = graphQlSetup -> {
			WebGraphQlRequest request = request("{ books {name}}");
			Mono<WebGraphQlResponse> responseMono = graphQlSetup.toWebGraphQlHandler().handleRequest(request);

			List<String> names = ResponseHelper.forResponse(responseMono).toList("books", Book.class)
					.stream().map(Book::getName).collect(Collectors.toList());

			assertThat(names).containsExactlyInAnyOrder(book1.getName(), book2.getName());
		};

		// explicit wiring
		tester.accept(graphQlSetup("books", QuerydslDataFetcher.builder(mockRepository).many()));

		// auto registration
		tester.accept(graphQlSetup(mockRepository));
	}

	@Disabled
	@Test
	void shouldFetchWindow() {

		// KeyValueRepositoryFactory doesn't have pagination support yet:
		// https://github.com/spring-projects/spring-data-keyvalue/issues/490

		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg"));
		mockRepository.saveAll(Arrays.asList(book1, book2));

		Consumer<GraphQlSetup> tester = graphQlSetup -> {

			Mono<WebGraphQlResponse> response = graphQlSetup
					.toWebGraphQlHandler()
					.handleRequest(request(BookSource.booksConnectionQuery("")));

			ResponseHelper.forResponse(response).assertData(
					"{\"books\":{" +
					"\"edges\":[" +
					"{\"cursor\":\"O_0\",\"node\":{\"id\":\"42\",\"name\":\"Hitchhiker's Guide to the Galaxy\"}}," +
					"{\"cursor\":\"O_1\",\"node\":{\"id\":\"53\",\"name\":\"Breaking Bad\"}}" +
					"]," +
					"\"pageInfo\":{" +
					"\"startCursor\":\"O_0\"," +
					"\"endCursor\":\"O_1\"," +
					"\"hasPreviousPage\":true," +
					"\"hasNextPage\":false" +
					"}}}"
			);
		};

		// explicit wiring

		ScrollPositionCursorStrategy cursorStrategy = new ScrollPositionCursorStrategy();

		DataFetcher<Iterable<Book>> dataFetcher =
				QuerydslDataFetcher.builder(mockRepository).cursorStrategy(cursorStrategy).scrollable();

		GraphQlSetup graphQlSetup = paginationSetup(cursorStrategy).queryFetcher("books", dataFetcher);
		tester.accept(graphQlSetup);

		// auto registration
		graphQlSetup = paginationSetup(cursorStrategy).runtimeWiring(createRuntimeWiringConfigurer(mockRepository, null));
		tester.accept(graphQlSetup);
	}

	@Test
	void shouldFetchMultipleItemsWithListInput() {
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg"));
		mockRepository.saveAll(Arrays.asList(book1, book2));

		Mono<WebGraphQlResponse> responseMono = graphQlSetup(mockRepository).toWebGraphQlHandler()
				.handleRequest(request("{ booksById(id: [42,53]) {name}}"));

		List<String> names = ResponseHelper.forResponse(responseMono).toList("booksById", Book.class)
				.stream().map(Book::getName).collect(Collectors.toList());

		assertThat(names).containsExactlyInAnyOrder(book1.getName(), book2.getName());
	}

	@Test
	void shouldApplyCustomizerInRepository() {
		MockWithCustomizerRepository repository = repositoryFactory.getRepository(MockWithCustomizerRepository.class);
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg"));
		repository.saveAll(Arrays.asList(book1, book2));

		Consumer<GraphQlSetup> tester = graphQlSetup -> {
			WebGraphQlRequest request = request("{ books {name}}");
			Mono<WebGraphQlResponse> responseMono = graphQlSetup.toWebGraphQlHandler().handleRequest(request);

			List<String> names = ResponseHelper.forResponse(responseMono).toList("books", Book.class)
					.stream().map(Book::getName).collect(Collectors.toList());

			assertThat(names).containsExactlyInAnyOrder(book1.getName(), book2.getName());
		};

		// explicit wiring
		tester.accept(graphQlSetup("books", QuerydslDataFetcher.builder(mockRepository).many()));

		// auto registration
		tester.accept(graphQlSetup(mockRepository));
	}

	@Test
	void shouldApplyCustomizerViaBuilder() {
		MockRepository mockRepository = mock(MockRepository.class);

		DataFetcher<Iterable<Book>> fetcher = QuerydslDataFetcher.builder(mockRepository)
				.customizer((QuerydslBinderCustomizer<QBook>) (bindings, book) ->
						bindings.bind(book.name).firstOptional((path, value) -> value.map(path::startsWith)))
				.many();

		graphQlSetup("books", fetcher).toWebGraphQlHandler()
				.handleRequest(request("{ books(name: \"H\", author: \"Doug\") {name}}"))
				.block();

		ArgumentCaptor<Predicate> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
		verify(mockRepository).findBy(predicateCaptor.capture(), any());

		Predicate predicate = predicateCaptor.getValue();
		assertThat(predicate).isEqualTo(QBook.book.name.startsWith("H").and(QBook.book.author.eq("Doug")));
	}

	@Test
	void shouldFavorExplicitWiring() {
		MockRepository mockRepository = mock(MockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		given(mockRepository.findBy(any(), any())).willReturn(Optional.of(book));

		// 1) Automatic registration only
		WebGraphQlHandler handler = graphQlSetup(mockRepository).toWebGraphQlHandler();
		Mono<WebGraphQlResponse> responseMono = handler.handleRequest(request("{ bookById(id: 1) {name}}"));

		Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("Hitchhiker's Guide to the Galaxy");

		// 2) Automatic registration and explicit wiring
		handler = graphQlSetup(mockRepository)
				.queryFetcher("bookById", env -> new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg")))
				.toWebGraphQlHandler();

		responseMono = handler.handleRequest(request("{ bookById(id: 1) {name}}"));

		actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("Breaking Bad");
	}

	@Test
	void shouldFetchSingleItemsWithInterfaceProjection() {
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		mockRepository.save(book);

		DataFetcher<?> fetcher = QuerydslDataFetcher.builder(mockRepository).projectAs(BookProjection.class).single();
		WebGraphQlHandler handler = graphQlSetup("bookById", fetcher).toWebGraphQlHandler();

		Mono<WebGraphQlResponse> responseMono = handler.handleRequest(request("{ bookById(id: 42) {name}}"));

		Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("Hitchhiker's Guide to the Galaxy by Douglas Adams");
	}

	@Test
	void shouldFetchSingleItemsWithDtoProjection() {
		MockWithBuilderCustomizerRepository mockWithCustomizerRepository = repositoryFactory.getRepository(MockWithBuilderCustomizerRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		mockWithCustomizerRepository.save(book);

		Consumer<GraphQlSetup> tester = graphQlSetup -> {
			WebGraphQlRequest request = request("{ bookById(id: 42) {name}}");
			Mono<WebGraphQlResponse> responseMono = graphQlSetup.toWebGraphQlHandler().handleRequest(request);

			Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);

			assertThat(actualBook.getName()).isEqualTo("The book is: " + book.getName());
		};

		// explicit wiring
		tester.accept(graphQlSetup(mockWithCustomizerRepository));
	}

	@Test
	void shouldReactivelyFetchSingleItems() {
		ReactiveMockRepository mockRepository = mock(ReactiveMockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		given(mockRepository.findBy(any(), any())).willReturn(Mono.just(book));

		Consumer<GraphQlSetup> tester = setup -> {
			WebGraphQlRequest request = request("{ bookById(id: 1) {name}}");
			Mono<WebGraphQlResponse> responseMono = setup.toWebGraphQlHandler().handleRequest(request);
			Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);

			assertThat(actualBook.getName()).isEqualTo(book.getName());
		};

		// explicit wiring
		tester.accept(graphQlSetup("bookById", QuerydslDataFetcher.builder(mockRepository).single()));

		// auto registration
		tester.accept(graphQlSetup(mockRepository));
	}

	@Test
	void shouldReactivelyFetchMultipleItems() {
		ReactiveMockRepository mockRepository = mock(ReactiveMockRepository.class);
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg"));
		given(mockRepository.findBy(any(), any())).willReturn(Flux.just(book1, book2));

		Consumer<GraphQlSetup> tester = setup -> {
			WebGraphQlRequest request = request("{ books {name}}");
			Mono<WebGraphQlResponse> responseMono = setup.toWebGraphQlHandler().handleRequest(request);

			List<String> names = ResponseHelper.forResponse(responseMono).toList("books", Book.class)
					.stream().map(Book::getName).collect(Collectors.toList());

			assertThat(names).containsExactlyInAnyOrder("Breaking Bad", "Hitchhiker's Guide to the Galaxy");
		};

		// explicit wiring
		tester.accept(graphQlSetup("books", QuerydslDataFetcher.builder(mockRepository).many()));

		// auto registration
		tester.accept(graphQlSetup(mockRepository));
	}

	@Test
	void shouldNestForSingleArgumentInputType() {
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg"));
		mockRepository.saveAll(Arrays.asList(book1, book2));

		String queryName = "booksByCriteria";

		Mono<ExecutionGraphQlResponse> responseMono =
				graphQlSetup(queryName, QuerydslDataFetcher.builder(mockRepository).many())
						.toGraphQlService()
						.execute(request("{" + queryName + "(criteria: {id: 42}) {name}}"));

		List<Book> books = ResponseHelper.forResponse(responseMono).toList(queryName, Book.class);

		assertThat(books).hasSize(1);
		assertThat(books.get(0).getName()).isEqualTo(book1.getName());
	}

	private static GraphQlSetup graphQlSetup(String fieldName, DataFetcher<?> fetcher) {
		return GraphQlSetup.schemaResource(BookSource.schema).queryFetcher(fieldName, fetcher);
	}

	private static GraphQlSetup graphQlSetup(@Nullable QuerydslPredicateExecutor<?> executor) {
		return GraphQlSetup.schemaResource(BookSource.schema)
				.runtimeWiring(createRuntimeWiringConfigurer(executor, null));
	}

	private static GraphQlSetup graphQlSetup(@Nullable ReactiveQuerydslPredicateExecutor<?> executor) {
		return GraphQlSetup.schemaResource(BookSource.schema)
				.runtimeWiring(createRuntimeWiringConfigurer(null, executor));
	}

	private static GraphQlSetup paginationSetup(ScrollPositionCursorStrategy cursorStrategy) {
		return GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.connectionSupport(new WindowConnectionAdapter(cursorStrategy));
	}

	private static RuntimeWiringConfigurer createRuntimeWiringConfigurer(
			@Nullable QuerydslPredicateExecutor<?> executor,
			@Nullable ReactiveQuerydslPredicateExecutor<?> reactiveExecutor) {

		return QuerydslDataFetcher.autoRegistrationConfigurer(
				(executor != null ? Collections.singletonList(executor) : Collections.emptyList()),
				(reactiveExecutor != null ? Collections.singletonList(reactiveExecutor) : Collections.emptyList()));
	}

	private WebGraphQlRequest request(String query) {
		return new WebGraphQlRequest(
				URI.create("/"), new HttpHeaders(), null, null, Collections.emptyMap(),
				new DefaultGraphQlRequest(query), "1", Locale.ENGLISH);
	}


	@GraphQlRepository
	interface MockRepository extends CrudRepository<Book, Long>, QuerydslPredicateExecutor<Book> {
	}

	@GraphQlRepository
	interface MockWithBuilderCustomizerRepository extends CrudRepository<Book, Long>, QuerydslPredicateExecutor<Book>, QuerydslBuilderCustomizer<Book> {

		@Override
		default Builder<Book, ?> customize(Builder<Book, ?> builder) {
			return builder.projectAs(BookDto.class);
		}
	}


	@GraphQlRepository
	interface MockWithCustomizerRepository extends CrudRepository<Book, Long>, QuerydslPredicateExecutor<Book>,
			QuerydslBinderCustomizer<QBook> {

		@Override
		default void customize(QuerydslBindings bindings, QBook book) {
			bindings.bind(book.name).firstOptional((path, value) -> value.map(path::startsWith));
		}

	}


	@GraphQlRepository
	interface ReactiveMockRepository extends Repository<Book, Long>, ReactiveQuerydslPredicateExecutor<Book> {
	}


	interface BookProjection {

		@Value("#{target.name + ' by ' + target.author.firstName + ' ' + target.author.lastName}")
		String getName();

	}


	static class BookDto {

		private final String name;

		public BookDto(String name) {
			this.name = name;
		}

		public String getName() {
			return "The book is: " + name;
		}

	}

}

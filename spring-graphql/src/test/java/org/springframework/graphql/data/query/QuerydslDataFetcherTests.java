/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.querydsl.core.types.Predicate;
import graphql.schema.DataFetcher;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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

	@Disabled("Not yet supported in spring-data-keyvalues")
	@Test
	void shouldFetchWindow() {

		// KeyValueRepositoryFactory doesn't have pagination support yet:
		// https://github.com/spring-projects/spring-data-keyvalue/issues/490

		mockRepository.saveAll(List.of(
				new Book(1L, "Nineteen Eighty-Four", new Author(101L, "George", "Orwell")),
				new Book(2L, "The Great Gatsby", new Author(102L, "F. Scott", "Fitzgerald")),
				new Book(3L, "Catch-22", new Author(103L, "Joseph", "Heller")),
				new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(105L, "Douglas", "Adams")),
				new Book(53L, "Breaking Bad", new Author(106L, "Vince", "Gilligan"))));

		Consumer<GraphQlSetup> tester = graphQlSetup -> {

			Mono<WebGraphQlResponse> response = graphQlSetup
					.toWebGraphQlHandler()
					.handleRequest(request(BookSource.booksConnectionQuery("first:2, after:\"O_2\"")));

			List<Map<String, Object>> edges = ResponseHelper.forResponse(response).toEntity("books.edges", List.class);
			assertThat(edges.size()).isEqualTo(2);
			assertThat(edges.get(0).get("cursor")).isEqualTo("O_3");
			assertThat(edges.get(1).get("cursor")).isEqualTo("O_4");

			Map<String, Object> pageInfo = ResponseHelper.forResponse(response).toEntity("books.pageInfo", Map.class);
			assertThat(pageInfo.size()).isEqualTo(4);
			assertThat(pageInfo.get("startCursor")).isEqualTo("O_3");
			assertThat(pageInfo.get("endCursor")).isEqualTo("O_4");
			assertThat(pageInfo.get("hasPreviousPage")).isEqualTo(true);
			assertThat(pageInfo.get("hasNextPage")).isEqualTo(false);
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

	@Disabled("Not yet supported in spring-data-keyvalues")
	@Test
	void shouldClampScrollCountToConfiguredMaximum() {

		// KeyValueRepositoryFactory doesn't have pagination support yet:
		// https://github.com/spring-projects/spring-data-keyvalue/issues/490

		mockRepository.saveAll(List.of(
				new Book(1L, "Nineteen Eighty-Four", new Author(101L, "George", "Orwell")),
				new Book(2L, "The Great Gatsby", new Author(102L, "F. Scott", "Fitzgerald")),
				new Book(3L, "Catch-22", new Author(103L, "Joseph", "Heller")),
				new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(105L, "Douglas", "Adams")),
				new Book(53L, "Breaking Bad", new Author(106L, "Vince", "Gilligan"))));

		ScrollPositionCursorStrategy cursorStrategy = new ScrollPositionCursorStrategy();

		DataFetcher<Iterable<Book>> dataFetcher = QuerydslDataFetcher.builder(mockRepository)
				.cursorStrategy(cursorStrategy)
				.maximumScrollCount(2)
				.scrollable();

		GraphQlSetup graphQlSetup = paginationSetup(cursorStrategy).queryFetcher("books", dataFetcher);

		Mono<WebGraphQlResponse> response = graphQlSetup
				.toWebGraphQlHandler()
				.handleRequest(request(BookSource.booksConnectionQuery("first:5")));

		List<Map<String, Object>> edges = ResponseHelper.forResponse(response).toEntity("books.edges",
				new ParameterizedTypeReference<>() {
				});
		assertThat(edges.size()).isEqualTo(2);
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
				.handleRequest(request("{ books(name: \"H\") {name}}"))
				.block();

		ArgumentCaptor<Predicate> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
		verify(mockRepository).findBy(predicateCaptor.capture(), any());

		Predicate predicate = predicateCaptor.getValue();
		assertThat(predicate).isEqualTo(QBook.book.name.startsWith("H"));
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

	@Test // gh-1081
	void shouldConsiderNestedArguments() {
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(0L, "", "Heisenberg"));
		mockRepository.saveAll(Arrays.asList(book1, book2));

		String queryName = "booksByNestedCriteria";

		Mono<ExecutionGraphQlResponse> responseMono =
				graphQlSetup(queryName, QuerydslDataFetcher.builder(mockRepository).many())
						.toGraphQlService()
						.execute(request("{" + queryName + "(author: {firstName: \"Douglas\"}) {name}}"));

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

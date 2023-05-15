/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.data.query.jpa;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import graphql.schema.DataFetcher;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.data.pagination.ConnectionFieldTypeVisitor;
import org.springframework.graphql.data.query.QueryByExampleDataFetcher;
import org.springframework.graphql.data.query.ScrollPositionCursorStrategy;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.graphql.data.query.WindowConnectionAdapter;
import org.springframework.graphql.execution.ConnectionTypeDefinitionConfigurer;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryByExampleDataFetcher}.
 */
@SpringJUnitConfig
class QueryByExampleDataFetcherJpaTests {

	@Autowired
	private BookJpaRepository repository;

	@Autowired
	private ProjectingBookJpaRepository projectingRepository;


	@Test
	void shouldFetchSingleItems() {
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(105L, "Douglas", "Adams"));
		repository.save(book);

		Consumer<GraphQlSetup> tester = setup -> {
			WebGraphQlRequest request = request("{ bookById(id: 42) {name}}");
			Mono<WebGraphQlResponse> responseMono = setup.toWebGraphQlHandler().handleRequest(request);
			Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);

			assertThat(actualBook.getName()).isEqualTo(book.getName());
		};

		// explicit wiring
		tester.accept(graphQlSetup("bookById", QueryByExampleDataFetcher.builder(repository).single()));

		// auto registration
		tester.accept(graphQlSetup(repository));
	}

	@Test
	void shouldFetchMultipleItems() {
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(105L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(106L, "Vince", "Gilligan"));
		repository.saveAll(Arrays.asList(book1, book2));

		Consumer<GraphQlSetup> tester = graphQlSetup -> {
			WebGraphQlRequest request = request("{ books {name}}");
			Mono<WebGraphQlResponse> responseMono = graphQlSetup.toWebGraphQlHandler().handleRequest(request);

			List<String> names = ResponseHelper.forResponse(responseMono).toList("books", Book.class)
					.stream()
					.map(Book::getName)
					.collect(Collectors.toList());

			assertThat(names).containsExactlyInAnyOrder(book1.getName(), book2.getName());
		};

		// explicit wiring
		tester.accept(graphQlSetup("books", QueryByExampleDataFetcher.builder(repository).many()));

		// auto registration
		tester.accept(graphQlSetup(repository));
	}

	@Test
	void shouldFetchWindow() {

		repository.saveAll(List.of(
				new Book(1L, "Nineteen Eighty-Four", new Author(101L, "George", "Orwell")),
				new Book(2L, "The Great Gatsby", new Author(102L, "F. Scott", "Fitzgerald")),
				new Book(3L, "Catch-22", new Author(103L, "Joseph", "Heller")),
				new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(105L, "Douglas", "Adams")),
				new Book(53L, "Breaking Bad", new Author(106L, "Vince", "Gilligan"))));

		Consumer<GraphQlSetup> tester = graphQlSetup -> {

			Mono<WebGraphQlResponse> response = graphQlSetup
					.toWebGraphQlHandler()
					.handleRequest(request(BookSource.booksConnectionQuery("first:2, after:\"O_3\"")));

			List<Map<String, Object>> edges = ResponseHelper.forResponse(response).toEntity("books.edges", List.class);
			assertThat(edges.size()).isEqualTo(2);
			assertThat(edges.get(0).get("cursor")).isEqualTo("O_4");
			assertThat(edges.get(1).get("cursor")).isEqualTo("O_5");

			Map<String, Object> pageInfo = ResponseHelper.forResponse(response).toEntity("books.pageInfo", Map.class);
			assertThat(pageInfo.size()).isEqualTo(4);
			assertThat(pageInfo.get("startCursor")).isEqualTo("O_4");
			assertThat(pageInfo.get("endCursor")).isEqualTo("O_5");
			assertThat(pageInfo.get("hasPreviousPage")).isEqualTo(true);
			assertThat(pageInfo.get("hasNextPage")).isEqualTo(false);
		};

		// explicit wiring

		ScrollPositionCursorStrategy cursorStrategy = new ScrollPositionCursorStrategy();

		DataFetcher<Iterable<Book>> dataFetcher =
				QueryByExampleDataFetcher.builder(repository).cursorStrategy(cursorStrategy).scrollable();

		GraphQlSetup graphQlSetup = paginationSetup(cursorStrategy).queryFetcher("books", dataFetcher);
		tester.accept(graphQlSetup);

		// auto registration
		graphQlSetup = paginationSetup(cursorStrategy).runtimeWiring(createRuntimeWiringConfigurer(repository));
		tester.accept(graphQlSetup);
	}

	@Test
	void shouldFavorExplicitWiring() {
		BookJpaRepository mockRepository = mock(BookJpaRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		when(mockRepository.findBy(any(), any())).thenReturn(Optional.of(book));

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
		repository.save(book);

		WebGraphQlHandler handler = graphQlSetup(projectingRepository).toWebGraphQlHandler();

		Mono<WebGraphQlResponse> responseMono = handler.handleRequest(request("{ bookById(id: 42) {name}}"));

		Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("Hitchhiker's Guide to the Galaxy by Douglas Adams");
	}

	@Disabled("Pending https://github.com/spring-projects/spring-data-jpa/issues/2327")
	@Test
	void shouldFetchSingleItemsWithDtoProjection() {
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		repository.save(book);

		DataFetcher<?> fetcher = QueryByExampleDataFetcher.builder(repository).projectAs(BookDto.class).single();
		WebGraphQlHandler handler = graphQlSetup("bookById", fetcher).toWebGraphQlHandler();

		Mono<WebGraphQlResponse> responseMono = handler.handleRequest(request("{ bookById(id: 42) {name}}"));

		Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("The book is: Hitchhiker's Guide to the Galaxy");
	}

	@Test
	void shouldNestForSingleArgumentInputType() {
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", new Author(0L, "Douglas", "Adams"));
		Book book2 = new Book(53L, "Breaking Bad", new Author(1L, "", "Heisenberg"));
		repository.saveAll(Arrays.asList(book1, book2));

		String queryName = "booksByCriteria";

		Mono<ExecutionGraphQlResponse> responseMono =
				graphQlSetup(queryName, QueryByExampleDataFetcher.builder(repository).many())
						.toGraphQlService()
						.execute(request("{" + queryName + "(criteria: {id: 42}) {name}}"));

		List<Book> books = ResponseHelper.forResponse(responseMono).toList(queryName, Book.class);

		assertThat(books).hasSize(1);
		assertThat(books.get(0).getName()).isEqualTo(book1.getName());
	}

	private static GraphQlSetup graphQlSetup(String fieldName, DataFetcher<?> fetcher) {
		return GraphQlSetup.schemaResource(BookSource.schema).queryFetcher(fieldName, fetcher);
	}

	private static GraphQlSetup graphQlSetup(QueryByExampleExecutor<?> executor) {
		return GraphQlSetup.schemaResource(BookSource.schema)
				.runtimeWiring(createRuntimeWiringConfigurer(executor));
	}

	private static GraphQlSetup paginationSetup(ScrollPositionCursorStrategy cursorStrategy) {
		return GraphQlSetup.schemaResource(BookSource.paginationSchema)
				.typeDefinitionConfigurer(new ConnectionTypeDefinitionConfigurer())
				.typeVisitor(ConnectionFieldTypeVisitor.create(List.of(new WindowConnectionAdapter(cursorStrategy))));
	}

	private static RuntimeWiringConfigurer createRuntimeWiringConfigurer(QueryByExampleExecutor<?> executor) {
		return QueryByExampleDataFetcher.autoRegistrationConfigurer(
				executor != null ? Collections.singletonList(executor) : Collections.emptyList(),
				Collections.emptyList(),
				new ScrollPositionCursorStrategy(),
				new ScrollSubrange(ScrollPosition.offset(), 10, true));
	}

	private WebGraphQlRequest request(String query) {
		return new WebGraphQlRequest(
				URI.create("/"), new HttpHeaders(), null, Collections.emptyMap(),
				Collections.singletonMap("query", query), "1", null);
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


	@Configuration
	@EnableJpaRepositories(considerNestedRepositories = true)
	static class TestConfig {

		@Bean
		DriverManagerDataSource dataSource() {
			DriverManagerDataSource dataSource = new DriverManagerDataSource();
			dataSource.setDriverClassName("org.h2.Driver");
			dataSource.setUrl("jdbc:h2:mem:query-by-example-test;DB_CLOSE_DELAY=-1");
			return dataSource;
		}

		@Bean
		LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
			HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
			jpaVendorAdapter.setGenerateDdl(true);
			jpaVendorAdapter.setShowSql(true);

			LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
			factory.setDataSource(dataSource);
			factory.setJpaVendorAdapter(jpaVendorAdapter);
			factory.setPackagesToScan(QueryByExampleDataFetcherJpaTests.class.getPackage().getName());
			return factory;
		}

		@Bean
		PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
			JpaTransactionManager transactionManager = new JpaTransactionManager();
			transactionManager.setEntityManagerFactory(entityManagerFactory);
			return transactionManager;
		}
	}

}

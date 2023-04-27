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

package org.springframework.graphql.data.query.mongo;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.mongodb.client.MongoClients;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.graphql.BookSource;
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
import org.springframework.lang.Nullable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryByExampleDataFetcher}.
 */
@SpringJUnitConfig
@Testcontainers(disabledWithoutDocker = true)
class QueryByExampleDataFetcherMongoDbTests {

	@Container
	static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));

	@Autowired
	private BookMongoRepository repository;


	@Test
	void shouldFetchSingleItems() {
		Book book = new Book("42", "Hitchhiker's Guide to the Galaxy", new Author("0", "Douglas", "Adams"));
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
		Book book1 = new Book("42", "Hitchhiker's Guide to the Galaxy", new Author("0", "Douglas", "Adams"));
		Book book2 = new Book("53", "Breaking Bad", new Author("0", "", "Heisenberg"));
		repository.saveAll(Arrays.asList(book1, book2));

		Consumer<GraphQlSetup> tester = graphQlSetup -> {
			WebGraphQlRequest request = request("{ books {name}}");
			Mono<WebGraphQlResponse> responseMono = graphQlSetup.toWebGraphQlHandler().handleRequest(request);

			List<String> names = ResponseHelper.forResponse(responseMono).toList("books", Book.class)
					.stream().map(Book::getName).collect(Collectors.toList());

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
				new Book("1", "Nineteen Eighty-Four", new Author("0", "George", "Orwell")),
				new Book("2", "The Great Gatsby", new Author("0", "F. Scott", "Fitzgerald")),
				new Book("3", "Catch-22", new Author("0", "Joseph", "Heller")),
				new Book("42", "Hitchhiker's Guide to the Galaxy", new Author("0", "Douglas", "Adams")),
				new Book("53", "Breaking Bad", new Author("0", "", "Heisenberg"))));

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
		BookMongoRepository mockRepository = mock(BookMongoRepository.class);
		Book book = new Book("42", "Hitchhiker's Guide to the Galaxy", new Author("0", "Douglas", "Adams"));
		when(mockRepository.findBy(any(), any())).thenReturn(Optional.of(book));

		// 1) Automatic registration only
		WebGraphQlHandler handler = graphQlSetup(mockRepository).toWebGraphQlHandler();
		Mono<WebGraphQlResponse> responseMono = handler.handleRequest(request("{ bookById(id: 1) {name}}"));

		Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("Hitchhiker's Guide to the Galaxy");

		// 2) Automatic registration and explicit wiring
		handler = graphQlSetup(mockRepository)
				.queryFetcher("bookById", env -> new Book("53", "Breaking Bad", new Author("0", "", "Heisenberg")))
				.toWebGraphQlHandler();

		responseMono = handler.handleRequest(request("{ bookById(id: 1) {name}}"));

		actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("Breaking Bad");
	}

	@Test
	void shouldFetchSingleItemsWithInterfaceProjection() {
		Book book = new Book("42", "Hitchhiker's Guide to the Galaxy", new Author("0", "Douglas", "Adams"));
		repository.save(book);

		DataFetcher<?> fetcher = QueryByExampleDataFetcher.builder(repository).projectAs(BookProjection.class).single();
		WebGraphQlHandler handler = graphQlSetup("bookById", fetcher).toWebGraphQlHandler();

		Mono<WebGraphQlResponse> responseMono = handler.handleRequest(request("{ bookById(id: 42) {name}}"));

		Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("Hitchhiker's Guide to the Galaxy by Douglas Adams");
	}

	@Test
	void shouldFetchSingleItemsWithDtoProjection() {
		Book book = new Book("42", "Hitchhiker's Guide to the Galaxy", new Author("0", "Douglas", "Adams"));
		repository.save(book);

		DataFetcher<?> fetcher = QueryByExampleDataFetcher.builder(repository).projectAs(BookDto.class).single();
		WebGraphQlHandler handler = graphQlSetup("bookById", fetcher).toWebGraphQlHandler();

		Mono<WebGraphQlResponse> responseMono = handler.handleRequest(request("{ bookById(id: 42) {name}}"));

		Book actualBook = ResponseHelper.forResponse(responseMono).toEntity("bookById", Book.class);
		assertThat(actualBook.getName()).isEqualTo("The book is: Hitchhiker's Guide to the Galaxy");
	}

	private static GraphQlSetup graphQlSetup(String fieldName, DataFetcher<?> fetcher) {
		return GraphQlSetup.schemaResource(BookSource.schema).queryFetcher(fieldName, fetcher);
	}

	private static GraphQlSetup graphQlSetup(@Nullable QueryByExampleExecutor<?> executor) {
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
				(executor != null ? Collections.singletonList(executor) : Collections.emptyList()),
				Collections.emptyList(),
				new ScrollPositionCursorStrategy(),
				new ScrollSubrange(ScrollPosition.offset(), 10, true));
	}

	private WebGraphQlRequest request(String query) {
		return new WebGraphQlRequest(
				URI.create("/"), new HttpHeaders(), null, Collections.emptyMap(),
				Collections.singletonMap("query", query), "1", null);
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


	@Configuration
	@EnableMongoRepositories(considerNestedRepositories = true)
	static class TestConfig {

		@Bean
		MongoTemplate mongoTemplate() {
			return new MongoTemplate(MongoClients.create(String.format("mongodb://%s:%d",
					mongoDBContainer.getContainerIpAddress(),
					mongoDBContainer.getFirstMappedPort())), "test");
		}
	}

}

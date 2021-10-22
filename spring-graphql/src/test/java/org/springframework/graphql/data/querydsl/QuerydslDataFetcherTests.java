/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.graphql.data.querydsl;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.querydsl.core.types.Predicate;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.idl.TypeRuntimeWiring;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.keyvalue.core.KeyValueTemplate;
import org.springframework.data.keyvalue.repository.support.KeyValueRepositoryFactory;
import org.springframework.data.map.MapKeyValueAdapter;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.graphql.data.GraphQlRepository;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QuerydslDataFetcher}.
 */
class QuerydslDataFetcherTests {

	private KeyValueRepositoryFactory repositoryFactory = new KeyValueRepositoryFactory(new KeyValueTemplate(new MapKeyValueAdapter()));
	private MockRepository mockRepository = repositoryFactory.getRepository(MockRepository.class);

	@Test
	void shouldFetchSingleItems() {
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		mockRepository.save(book);

		BiConsumer<Consumer<TypeRuntimeWiring.Builder>, QuerydslPredicateExecutor<?>> tester =
				(wiringConfigurer, executor) -> {
					WebGraphQlHandler handler = initWebGraphQlHandler(wiringConfigurer, executor, null);
					WebOutput output = handler.handleRequest(input("{ bookById(id: 42) {name}}")).block();

					// TODO: getData interferes with method overrides
					assertThat((Object) output.getData()).isEqualTo(
							Collections.singletonMap("bookById",
									Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy")));
				};

		// explicit wiring
		tester.accept(
				builder -> builder.dataFetcher("bookById", QuerydslDataFetcher.builder(mockRepository).single()),
				null);

		// auto registration
		tester.accept(null, mockRepository);
	}

	@Test
	void shouldFetchMultipleItems() {
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		Book book2 = new Book(53L, "Breaking Bad", "Heisenberg");
		mockRepository.saveAll(Arrays.asList(book1, book2));

		BiConsumer<Consumer<TypeRuntimeWiring.Builder>, QuerydslPredicateExecutor<?>> tester =
				(wiringConfigurer, executor) -> {
					WebGraphQlHandler handler = initWebGraphQlHandler(wiringConfigurer, mockRepository, null);
					WebOutput output = handler.handleRequest(input("{ books {name}}")).block();

					assertThat((Object) output.getData()).isEqualTo(
							Collections.singletonMap("books", Arrays.asList(
									Collections.singletonMap("name", "Breaking Bad"),
									Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy"))));
				};

		// explicit wiring
		tester.accept(
				builder -> builder.dataFetcher("books", QuerydslDataFetcher.builder(mockRepository).many()),
				null);

		// auto registration
		tester.accept(null, mockRepository);
	}

	@Test
	void shouldFavorExplicitWiring() {
		MockRepository mockRepository = mock(MockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		when(mockRepository.findBy(any(), any())).thenReturn(Optional.of(book));

		// 1) Automatic registration only
		WebGraphQlHandler handler = initWebGraphQlHandler(null, mockRepository, null);
		WebOutput output = handler.handleRequest(input("{ bookById(id: 1) {name}}")).block();

		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("bookById", Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy")));

		// 2) Automatic registration and explicit wiring
		handler = initWebGraphQlHandler(
				builder -> builder.dataFetcher("bookById", env -> new Book(53L, "Breaking Bad", "Heisenberg")),
				mockRepository, null);

		output = handler.handleRequest(input("{ bookById(id: 1) {name}}")).block();

		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("bookById", Collections.singletonMap("name", "Breaking Bad")));
	}

	@Test
	void shouldFetchSingleItemsWithInterfaceProjection() {
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		mockRepository.save(book);

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("bookById", QuerydslDataFetcher
						.builder(mockRepository)
						.projectAs(BookProjection.class)
						.single()));

		WebOutput output = handler.handleRequest(input("{ bookById(id: 42) {name}}")).block();

		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("bookById",
						Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy by Douglas Adams")));
	}

	@Test
	void shouldFetchSingleItemsWithDtoProjection() {
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		mockRepository.save(book);

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("bookById", QuerydslDataFetcher
						.builder(mockRepository)
						.projectAs(BookDto.class)
						.single()));

		WebOutput output = handler.handleRequest(input("{ bookById(id: 42) {name}}")).block();

		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("bookById",
						Collections.singletonMap("name", "The book is: Hitchhiker's Guide to the Galaxy")));
	}

	@Test
	void shouldConstructPredicateProperly() {
		MockRepository mockRepository = mock(MockRepository.class);

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("books", QuerydslDataFetcher
						.builder(mockRepository)
						.customizer((QuerydslBinderCustomizer<QBook>) (bindings, book) ->
								bindings.bind(book.name).firstOptional((path, value) -> value.map(path::startsWith)))
						.many()));

		handler.handleRequest(input("{ books(name: \"H\", author: \"Doug\") {name}}")).block();


		ArgumentCaptor<Predicate> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
		verify(mockRepository).findBy(predicateCaptor.capture(), any());

		Predicate predicate = predicateCaptor.getValue();
		assertThat(predicate).isEqualTo(QBook.book.name.startsWith("H").and(QBook.book.author.eq("Doug")));
	}

	@Test
	void shouldReactivelyFetchSingleItems() {
		ReactiveMockRepository mockRepository = mock(ReactiveMockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		when(mockRepository.findBy(any(), any())).thenReturn(Mono.just(book));

		BiConsumer<Consumer<TypeRuntimeWiring.Builder>, ReactiveQuerydslPredicateExecutor<?>> tester =
				(wiringConfigurer, executor) -> {
					WebGraphQlHandler handler = initWebGraphQlHandler(wiringConfigurer, null, executor);
					WebOutput output = handler.handleRequest(input("{ bookById(id: 1) {name}}")).block();

					// TODO: getData interferes with method overrides
					assertThat((Object) output.getData()).isEqualTo(
							Collections.singletonMap("bookById",
									Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy")));
				};

		// explicit wiring
		tester.accept(
				builder -> builder.dataFetcher("bookById", QuerydslDataFetcher.builder(mockRepository).single()),
				null);

		// auto registration
		tester.accept(null, mockRepository);
	}

	@Test
	void shouldReactivelyFetchMultipleItems() {
		ReactiveMockRepository mockRepository = mock(ReactiveMockRepository.class);
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		Book book2 = new Book(53L, "Breaking Bad", "Heisenberg");
		when(mockRepository.findBy(any(), any())).thenReturn(Flux.just(book1, book2));

		BiConsumer<Consumer<TypeRuntimeWiring.Builder>, ReactiveQuerydslPredicateExecutor<?>> tester =
				(wiringConfigurer, executor) -> {
					WebGraphQlHandler handler = initWebGraphQlHandler(wiringConfigurer, null, mockRepository);
					WebOutput output = handler.handleRequest(input("{ books {name}}")).block();

					assertThat((Object) output.getData()).isEqualTo(
							Collections.singletonMap("books", Arrays.asList(
									Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy"),
									Collections.singletonMap("name", "Breaking Bad"))));
				};

		// explicit wiring
		tester.accept(
				builder -> builder.dataFetcher("books", QuerydslDataFetcher.builder(mockRepository).many()),
				null);

		// auto registration
		tester.accept(null, mockRepository);
	}


	@GraphQlRepository
	interface MockRepository extends CrudRepository<Book, Long>, QuerydslPredicateExecutor<Book> {

	}

	@GraphQlRepository
	interface ReactiveMockRepository extends Repository<Book, Long>, ReactiveQuerydslPredicateExecutor<Book> {

	}

	static WebGraphQlHandler initWebGraphQlHandler(Consumer<TypeRuntimeWiring.Builder> configurer) {
		return initWebGraphQlHandler(configurer, null, null);
	}

	static WebGraphQlHandler initWebGraphQlHandler(
			@Nullable Consumer<TypeRuntimeWiring.Builder> configurer,
			@Nullable QuerydslPredicateExecutor<?> executor,
			@Nullable ReactiveQuerydslPredicateExecutor<?> reactiveExecutor) {

		return WebGraphQlHandler
				.builder(new ExecutionGraphQlService(graphQlSource(configurer, executor, reactiveExecutor)))
				.build();
	}

	private static GraphQlSource graphQlSource(
			@Nullable Consumer<TypeRuntimeWiring.Builder> configurer,
			@Nullable QuerydslPredicateExecutor<?> executor,
			@Nullable ReactiveQuerydslPredicateExecutor<?> reactiveExecutor) {

		GraphQlSource.Builder graphQlSourceBuilder = GraphQlSource.builder()
				.schemaResources(new ClassPathResource("books/schema.graphqls"));

		if (configurer != null) {
			TypeRuntimeWiring.Builder typeBuilder = TypeRuntimeWiring.newTypeWiring("Query");
			configurer.accept(typeBuilder);
			graphQlSourceBuilder.configureRuntimeWiring(wiring -> wiring.type(typeBuilder));
		}

		GraphQLTypeVisitor visitor = QuerydslDataFetcher.registrationTypeVisitor(
				(executor != null ? Collections.singletonList(executor) : Collections.emptyList()),
				(reactiveExecutor != null ? Collections.singletonList(reactiveExecutor): Collections.emptyList()));

		graphQlSourceBuilder.typeVisitors(Collections.singletonList(visitor));

		return graphQlSourceBuilder.build();
	}

	private WebInput input(String query) {
		return new WebInput(URI.create("http://abc.org"), new HttpHeaders(),
				Collections.singletonMap("query", query), "1");
	}

	interface BookProjection {

		@Value("#{target.name + ' by ' + target.author}")
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

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

package org.springframework.graphql.data;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import com.querydsl.core.types.Predicate;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.repository.Repository;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.http.HttpHeaders;

/**
 * Unit tests for {@link QuerydslDataFetcher}.
 */
class QuerydslDataFetcherTests {

	@Test
	void shouldFetchSingleItems() {
		MockRepository mockRepository = mock(MockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		when(mockRepository.findOne(any())).thenReturn(Optional.of(book));

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("bookById", QuerydslDataFetcher.builder(mockRepository).single()));

		WebOutput output = handler.handle(input("{ bookById(id: 1) {name}}")).block();

		// TODO: getData interferes with method overrides
		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("bookById",
						Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy")));
	}

	@Test
	void shouldFetchMultipleItems() {
		MockRepository mockRepository = mock(MockRepository.class);
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		Book book2 = new Book(53L, "Breaking Bad", "Heisenberg");
		when(mockRepository.findAll(any(Predicate.class))).thenReturn(Arrays.asList(book1, book2));

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("books", QuerydslDataFetcher.builder(mockRepository).many()));

		WebOutput output = handler.handle(input("{ books {name}}")).block();

		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("books", Arrays.asList(
						Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy"),
						Collections.singletonMap("name", "Breaking Bad"))));
	}

	@Test
	void shouldFetchSingleItemsWithInterfaceProjection() {
		MockRepository mockRepository = mock(MockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		when(mockRepository.findOne(any())).thenReturn(Optional.of(book));

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("bookById", QuerydslDataFetcher
						.builder(mockRepository)
						.projectAs(BookProjection.class)
						.single()));

		WebOutput output = handler.handle(input("{ bookById(id: 1) {name}}")).block();

		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("bookById",
						Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy by Douglas Adams")));
	}

	@Test
	void shouldFetchSingleItemsWithDtoProjection() {
		MockRepository mockRepository = mock(MockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		when(mockRepository.findOne(any())).thenReturn(Optional.of(book));

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("bookById", QuerydslDataFetcher
						.builder(mockRepository)
						.projectAs(BookDto.class)
						.single()));

		WebOutput output = handler.handle(input("{ bookById(id: 1) {name}}")).block();

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
								bindings.bind(book.name)
										.firstOptional((path, value) -> value.map(path::startsWith)))
						.many()));

		handler.handle(input("{ books(name: \"H\", author: \"Doug\") {name}}")).block();


		ArgumentCaptor<Predicate> predicateCaptor = ArgumentCaptor.forClass(Predicate.class);
		verify(mockRepository).findAll(predicateCaptor.capture());

		Predicate predicate = predicateCaptor.getValue();

		assertThat(predicate).isEqualTo(QBook.book.name.startsWith("H").and(QBook.book.author.eq("Doug")));
	}

	@Test
	void shouldReactivelyFetchSingleItems() {
		ReactiveMockRepository mockRepository = mock(ReactiveMockRepository.class);
		Book book = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		when(mockRepository.findOne(any())).thenReturn(Mono.just(book));

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("bookById", QuerydslDataFetcher.builder(mockRepository).single()));

		WebOutput output = handler.handle(input("{ bookById(id: 1) {name}}")).block();

		// TODO: getData interferes with method overries
		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("bookById",
						Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy")));
	}

	@Test
	void shouldReactivelyFetchMultipleItems() {
		ReactiveMockRepository mockRepository = mock(ReactiveMockRepository.class);
		Book book1 = new Book(42L, "Hitchhiker's Guide to the Galaxy", "Douglas Adams");
		Book book2 = new Book(53L, "Breaking Bad", "Heisenberg");
		when(mockRepository.findAll((Predicate) any())).thenReturn(Flux.just(book1, book2));

		WebGraphQlHandler handler = initWebGraphQlHandler(builder -> builder
				.dataFetcher("books", QuerydslDataFetcher.builder(mockRepository).many()));

		WebOutput output = handler.handle(input("{ books {name}}")).block();

		assertThat((Object) output.getData()).isEqualTo(
				Collections.singletonMap("books", Arrays.asList(
						Collections.singletonMap("name", "Hitchhiker's Guide to the Galaxy"),
						Collections.singletonMap("name", "Breaking Bad"))));
	}

	interface MockRepository extends Repository<Book, Long>, QuerydslPredicateExecutor<Book> {

	}

	interface ReactiveMockRepository extends Repository<Book, Long>, ReactiveQuerydslPredicateExecutor<Book> {

	}

	static WebGraphQlHandler initWebGraphQlHandler(Consumer<TypeRuntimeWiring.Builder> configurer) {
		return WebGraphQlHandler
				.builder(new ExecutionGraphQlService(graphQlSource(configurer)))
				.build();
	}

	private static GraphQlSource graphQlSource(Consumer<TypeRuntimeWiring.Builder> configurer) {
		RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
		TypeRuntimeWiring.Builder wiringBuilder = TypeRuntimeWiring.newTypeWiring("Query");
		configurer.accept(wiringBuilder);
		builder.type(wiringBuilder);
		return GraphQlSource.builder()
				.schemaResource(new ClassPathResource("books/schema.graphqls"))
				.runtimeWiring(builder.build())
				.build();
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

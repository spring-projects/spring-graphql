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
package org.springframework.graphql.data.method;

import java.util.Map;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlController;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnnotatedDataFetcherRegistrar}.
 * @author Rossen Stoyanchev
 */
public class AnnotatedDataFetcherRegistrarTests {

	@Test
	void registerWithDefaultCoordinates() {
		RuntimeWiring.Builder wiringBuilder = initRuntimeWiringBuilder(BookController.class);

		Map<String, Map<String, DataFetcher>> fetcherMap = wiringBuilder.build().getDataFetchers();
		assertThat(fetcherMap).containsOnlyKeys("Query", "Mutation", "Subscription", "Book");
		assertThat(fetcherMap.get("Query")).containsOnlyKeys("bookById", "bookByIdCustomized");
		assertThat(fetcherMap.get("Mutation")).containsOnlyKeys("saveBook", "saveBookCustomized");
		assertThat(fetcherMap.get("Subscription")).containsOnlyKeys("bookSearch", "bookSearchCustomized");
		assertThat(fetcherMap.get("Book")).containsOnlyKeys("author", "authorCustomized");

		checkMappedMethod(fetcherMap, "Query", "bookById", "bookById");
		checkMappedMethod(fetcherMap, "Mutation", "saveBook", "saveBook");
		checkMappedMethod(fetcherMap, "Subscription", "bookSearch", "bookSearch");
		checkMappedMethod(fetcherMap, "Book", "author", "author");
	}

	@Test
	void registerWithExplicitCoordinates() {
		RuntimeWiring.Builder wiringBuilder = initRuntimeWiringBuilder(BookController.class);

		Map<String, Map<String, DataFetcher>> fetcherMap = wiringBuilder.build().getDataFetchers();
		assertThat(fetcherMap).containsOnlyKeys("Query", "Mutation", "Subscription", "Book");
		assertThat(fetcherMap.get("Query")).containsOnlyKeys("bookById", "bookByIdCustomized");
		assertThat(fetcherMap.get("Mutation")).containsOnlyKeys("saveBook", "saveBookCustomized");
		assertThat(fetcherMap.get("Subscription")).containsOnlyKeys("bookSearch", "bookSearchCustomized");
		assertThat(fetcherMap.get("Book")).containsOnlyKeys("author", "authorCustomized");

		checkMappedMethod(fetcherMap, "Query", "bookByIdCustomized", "bookByIdWithNonMatchingMethodName");
		checkMappedMethod(fetcherMap, "Mutation", "saveBookCustomized", "saveBookWithNonMatchingMethodName");
		checkMappedMethod(fetcherMap, "Subscription", "bookSearchCustomized", "bookSearchWithNonMatchingMethodName");
		checkMappedMethod(fetcherMap, "Book", "authorCustomized", "authorWithNonMatchingMethodName");
	}

	private RuntimeWiring.Builder initRuntimeWiringBuilder(Class<?> handlerType) {
		AnnotationConfigApplicationContext appContext = new AnnotationConfigApplicationContext();
		appContext.registerBean(handlerType);
		appContext.refresh();

		AnnotatedDataFetcherRegistrar registrar = new AnnotatedDataFetcherRegistrar();
		registrar.setJsonMessageConverter(new MappingJackson2HttpMessageConverter());
		registrar.setApplicationContext(appContext);
		registrar.afterPropertiesSet();

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		registrar.register(wiringBuilder);
		return wiringBuilder;
	}

	@SuppressWarnings("rawtypes")
	private void checkMappedMethod(
			Map<String, Map<String, DataFetcher>> fetcherMap, String typeName, String fieldName, String methodName) {

		AnnotatedDataFetcher fetcher = (AnnotatedDataFetcher) fetcherMap.get(typeName).get(fieldName);
		assertThat(fetcher.getHandlerMethod().getMethod().getName()).isEqualTo(methodName);
	}


	@GraphQlController
	private static class BookController {


		@QueryMapping
		public Book bookById(@Argument String id) {
			return null;
		}

		@MutationMapping
		public void saveBook(Book book) {
		}

		@SubscriptionMapping
		public Flux<Book> bookSearch(@Argument String author) {
			return Flux.empty();
		}

		@SchemaMapping
		public Author author(DataFetchingEnvironment environment, Book book) {
			return null;
		}

		// Field name explicitly specified

		@QueryMapping("bookByIdCustomized")
		public Book bookByIdWithNonMatchingMethodName(@Argument String id) {
			return null;
		}

		@MutationMapping("saveBookCustomized")
		public void saveBookWithNonMatchingMethodName(Book book) {
		}

		@SubscriptionMapping("bookSearchCustomized")
		public Flux<Book> bookSearchWithNonMatchingMethodName(@Argument String author) {
			return Flux.empty();
		}

		@SchemaMapping("authorCustomized")
		public Author authorWithNonMatchingMethodName(Book book) {
			return null;
		}
	}

}

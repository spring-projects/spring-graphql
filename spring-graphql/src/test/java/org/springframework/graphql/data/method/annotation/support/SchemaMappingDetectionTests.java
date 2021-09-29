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
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnnotatedControllerConfigurer}, focusing on detection
 * and mapping of handler methods to schema fields.
 *
 * @author Rossen Stoyanchev
 */
public class SchemaMappingDetectionTests {

	@Test
	void registerWithDefaultCoordinates() {

		Map<String, Map<String, DataFetcher>> map =
				initRuntimeWiringBuilder(BookController.class).build().getDataFetchers();

		assertThat(map).containsOnlyKeys("Query", "Mutation", "Subscription", "Book");
		assertThat(map.get("Query")).containsOnlyKeys("bookById", "bookByIdCustomized");
		assertThat(map.get("Mutation")).containsOnlyKeys("saveBook", "saveBookCustomized");
		assertThat(map.get("Subscription")).containsOnlyKeys("bookSearch", "bookSearchCustomized");
		assertThat(map.get("Book")).containsOnlyKeys("author", "authorCustomized");

		assertMapping(map, "Query.bookById", "bookById");
		assertMapping(map, "Mutation.saveBook", "saveBook");
		assertMapping(map, "Subscription.bookSearch", "bookSearch");
		assertMapping(map, "Book.author", "author");
	}

	@Test
	void registerWithExplicitCoordinates() {

		Map<String, Map<String, DataFetcher>> map =
				initRuntimeWiringBuilder(BookController.class).build().getDataFetchers();

		assertThat(map).containsOnlyKeys("Query", "Mutation", "Subscription", "Book");
		assertThat(map.get("Query")).containsOnlyKeys("bookById", "bookByIdCustomized");
		assertThat(map.get("Mutation")).containsOnlyKeys("saveBook", "saveBookCustomized");
		assertThat(map.get("Subscription")).containsOnlyKeys("bookSearch", "bookSearchCustomized");
		assertThat(map.get("Book")).containsOnlyKeys("author", "authorCustomized");

		assertMapping(map, "Query.bookByIdCustomized", "bookByIdWithNonMatchingMethodName");
		assertMapping(map, "Mutation.saveBookCustomized", "saveBookWithNonMatchingMethodName");
		assertMapping(map, "Subscription.bookSearchCustomized", "bookSearchWithNonMatchingMethodName");
		assertMapping(map, "Book.authorCustomized", "authorWithNonMatchingMethodName");
	}

	private RuntimeWiring.Builder initRuntimeWiringBuilder(Class<?> handlerType) {
		AnnotationConfigApplicationContext appContext = new AnnotationConfigApplicationContext();
		appContext.registerBean(handlerType);
		appContext.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setApplicationContext(appContext);
		configurer.afterPropertiesSet();

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		configurer.configure(wiringBuilder);
		return wiringBuilder;
	}

	@SuppressWarnings("rawtypes")
	private void assertMapping(Map<String, Map<String, DataFetcher>> map, String coordinates, String methodName) {

		String[] strings = StringUtils.tokenizeToStringArray(coordinates, ".");
		String typeName = strings[0];
		String field = strings[1];

		AnnotatedControllerConfigurer.SchemaMappingDataFetcher dataFetcher =
				(AnnotatedControllerConfigurer.SchemaMappingDataFetcher) map.get(typeName).get(field);

		assertThat(dataFetcher.getHandlerMethod().getMethod().getName()).isEqualTo(methodName);
	}


	@Controller
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

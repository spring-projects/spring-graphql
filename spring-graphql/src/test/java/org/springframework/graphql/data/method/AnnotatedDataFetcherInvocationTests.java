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

import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookCriteria;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests with invocation of DataFetcher's from annotated methods.
 * @author Rossen Stoyanchev
 */
public class AnnotatedDataFetcherInvocationTests {

	@Test
	void queryWithScalarArgument() {
		String query = "{ " +
				"  bookById(id:\"1\") { " +
				"    id" +
				"    name" +
				"    author {" +
				"      firstName" +
				"      lastName" +
				"    }" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQl(BookController.class).execute(query);

		assertThat(result.getErrors()).isEmpty();
		Map<String, Object> data = result.getData();
		assertThat(data).isNotNull();

		Map<String, Object> book = getValue(data, "bookById");
		assertThat(book.get("id")).isEqualTo("1");
		assertThat(book.get("name")).isEqualTo("Nineteen Eighty-Four");

		Map<String, Object> author = getValue(book, "author");
		assertThat(author.get("firstName")).isEqualTo("George");
		assertThat(author.get("lastName")).isEqualTo("Orwell");
	}

	@Test
	void queryWithObjectArgument() {
		String query = "{ " +
				"  booksByCriteria(criteria: {author:\"Orwell\"}) { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQl(BookController.class).execute(query);

		assertThat(result.getErrors()).isEmpty();
		Map<String, Object> data = result.getData();
		assertThat(data).isNotNull();

		List<Map<String, Object>> bookList = getValue(data, "booksByCriteria");
		assertThat(bookList).hasSize(2);
		assertThat(bookList.get(0).get("name")).isEqualTo("Nineteen Eighty-Four");
		assertThat(bookList.get(1).get("name")).isEqualTo("Animal Farm");
	}

	@Test
	void queryWithArgumentViaDataFetchingEnvironment() {
		String query = "{ " +
				"  authorById(id:\"1\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		ExecutionInput input = ExecutionInput.newExecutionInput().query(query).build();
		ExecutionResult result = initGraphQl(BookController.class).execute(input);

		assertThat(result.getErrors()).isEmpty();
		Map<String, Object> data = result.getData();
		assertThat(data).isNotNull();

		Map<String, Object> author = getValue(data, "authorById");
		assertThat(author.get("id")).isEqualTo("1");
		assertThat(author.get("firstName")).isEqualTo("George");
		assertThat(author.get("lastName")).isEqualTo("Orwell");

		assertThat(input.getGraphQLContext().<String>get("key")).isEqualTo("value");
	}

	@Test
	void mutation() {
		String operation = "mutation { " +
				"  addAuthor(firstName:\"James\", lastName:\"Joyce\") { " +
				"    id" +
				"    firstName" +
				"    lastName" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQl(BookController.class).execute(operation);

		assertThat(result.getErrors()).isEmpty();
		Map<String, Object> data = result.getData();
		assertThat(data).isNotNull();

		Map<String, Object> author = getValue(data, "addAuthor");
		assertThat(author.get("id")).isEqualTo("99");
		assertThat(author.get("firstName")).isEqualTo("James");
		assertThat(author.get("lastName")).isEqualTo("Joyce");
	}

	@Test
	void subscription() {
		String operation = "subscription { " +
				"  bookSearch(author:\"Orwell\") { " +
				"    id" +
				"    name" +
				"  }" +
				"}";

		ExecutionResult result = initGraphQl(BookController.class).execute(operation);

		assertThat(result.getErrors()).isEmpty();
		Publisher<ExecutionResult> publisher = result.getData();
		assertThat(publisher).isNotNull();

		Flux<Map<String, Object>> bookFlux = Flux.from(publisher).map(rs -> {
			Map<String, Object> map = rs.getData();
			return (Map<String, Object>) map.get("bookSearch");
		});

		StepVerifier.create(bookFlux)
				.consumeNextWith(book -> {
					assertThat(book.get("id")).isEqualTo("1");
					assertThat(book.get("name")).isEqualTo("Nineteen Eighty-Four");
				})
				.consumeNextWith(book -> {
					assertThat(book.get("id")).isEqualTo("5");
					assertThat(book.get("name")).isEqualTo("Animal Farm");
				})
				.verifyComplete();
	}


	private GraphQL initGraphQl(Class<?> beanClass) {
		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
		applicationContext.registerBean(beanClass);
		applicationContext.refresh();

		AnnotatedDataFetcherConfigurer configurer = new AnnotatedDataFetcherConfigurer();
		configurer.setApplicationContext(applicationContext);
		configurer.setServerCodecConfigurer(ServerCodecConfigurer.create());
		configurer.afterPropertiesSet();

		GraphQlSource graphQlSource = GraphQlSource.builder()
				.schemaResources(new ClassPathResource("books/schema.graphqls"))
				.configureRuntimeWiring(configurer::configure)
				.build();

		return graphQlSource.graphQl();
	}

	@SuppressWarnings("unchecked")
	private <T> T getValue(Map<String, Object> data, String key) {
		return (T) data.get(key);
	}


	@Controller
	private static class BookController {

		@QueryMapping
		public Book bookById(@Argument Long id) {
			return new Book(id, BookSource.getBook(id).getName(), null);
		}

		@QueryMapping
		public List<Book> booksByCriteria(@Argument BookCriteria criteria) {
			return BookSource.findBooksByAuthor(criteria.getAuthor());
		}

		@SchemaMapping
		public Author author(Book book) {
			return BookSource.getBook(book.getId()).getAuthor();
		}

		@QueryMapping
		public Author authorById(DataFetchingEnvironment environment, GraphQLContext context) {
			context.put("key", "value");
			String id = environment.getArgument("id");
			return BookSource.getAuthor(Long.parseLong(id));
		}

		@MutationMapping
		public Author addAuthor(@Argument String firstName, @Argument String lastName) {
			return new Author(99L, firstName, lastName);
		}

		@SubscriptionMapping
		public Flux<Book> bookSearch(@Argument String author) {
			return Flux.fromIterable(BookSource.findBooksByAuthor(author));
		}
	}

}

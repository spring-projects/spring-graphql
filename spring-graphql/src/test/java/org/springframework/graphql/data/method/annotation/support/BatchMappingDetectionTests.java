/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AnnotatedControllerConfigurer}, focusing on detection
 * and mapping of handler methods to schema fields.
 *
 * @author Rossen Stoyanchev
 */
@SuppressWarnings({"rawtypes", "unused"})
public class BatchMappingDetectionTests {

	private final BatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();


	@Test
	void registerWithDefaultCoordinates() {

		Map<String, Map<String, DataFetcher>> dataFetcherMap =
				initRuntimeWiringBuilder(BookController.class).build().getDataFetchers();

		assertThat(dataFetcherMap).containsOnlyKeys("Book");
		assertThat(dataFetcherMap.get("Book")).containsOnlyKeys(
				"authorFlux", "authorList", "authorMonoMap", "authorMap", "authorCallableMap", "authorEnvironment");

		DataLoaderRegistry registry = new DataLoaderRegistry();
		this.batchLoaderRegistry.registerDataLoaders(registry, GraphQLContext.newContext().build());

		assertThat(registry.getDataLoadersMap()).containsOnlyKeys(
				"Book.authorFlux", "Book.authorList", "Book.authorMonoMap", "Book.authorMap",
				"Book.authorCallableMap", "Book.authorEnvironment");
	}

	@Test
	void registerWithMaxBatchSize() {

		BatchSizeController controller = new BatchSizeController();
		initRuntimeWiringBuilder(controller).build();
		DataLoaderRegistry registry = new DataLoaderRegistry();
		this.batchLoaderRegistry.registerDataLoaders(registry, GraphQLContext.newContext().build());

		DataLoader<Integer, String> dataLoader = registry.getDataLoader("Book.authors");
		dataLoader.loadMany(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
		dataLoader.dispatchAndJoin();

		assertThat(controller.getBatchSizes()).containsExactly(5, 3);

	}

	@Test
	void invalidReturnType() {
		assertThatThrownBy(() -> initRuntimeWiringBuilder(InvalidReturnTypeController.class).build())
				.hasMessageStartingWith("@BatchMapping method is expected to return");
	}

	@Test
	void schemaAndBatch() {
		assertThatThrownBy(() -> initRuntimeWiringBuilder(SchemaAndBatchMappingController.class).build())
				.hasMessageStartingWith("Expected either @BatchMapping or @SchemaMapping, not both");
	}

	@SuppressWarnings("unchecked")
	private <T> RuntimeWiring.Builder initRuntimeWiringBuilder(T handler) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		if (handler instanceof Class<?> handlerType) {
			context.registerBean(handlerType);
		}
		else {
			context.registerBean((Class<T>) handler.getClass(), () -> handler);
		}
		context.registerBean(BatchLoaderRegistry.class, () -> this.batchLoaderRegistry);
		context.refresh();

		AnnotatedControllerConfigurer configurer = new AnnotatedControllerConfigurer();
		configurer.setExecutor(new SimpleAsyncTaskExecutor());
		configurer.setApplicationContext(context);
		configurer.afterPropertiesSet();

		RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
		configurer.configure(wiringBuilder);
		return wiringBuilder;
	}


	@Controller
	@SuppressWarnings({"ConstantConditions", "unused"})
	private static class BookController {

		@BatchMapping
		public Flux<Author> authorFlux(List<Book> books) {
			return null;
		}

		@BatchMapping
		public List<Author> authorList(List<Book> books) {
			return null;
		}

		@BatchMapping
		public Mono<Map<Book, Author>> authorMonoMap(List<Book> books) {
			return null;
		}

		@BatchMapping
		public Map<Book, Author> authorMap(List<Book> books) {
			return null;
		}

		@BatchMapping
		public Callable<Map<Book, Author>> authorCallableMap(List<Book> books) {
			return null;
		}

		@BatchMapping
		public List<Author> authorEnvironment(BatchLoaderEnvironment environment, List<Book> books) {
			return null;
		}
	}


	@Controller
	@SuppressWarnings("unused")
	private static class BatchSizeController {

		private final List<Integer> batchSizes = new ArrayList<>();

		public List<Integer> getBatchSizes() {
			return this.batchSizes;
		}

		@BatchMapping(maxBatchSize = 5, typeName = "Book")
		public List<String> authors(List<Integer> bookIds) {
			this.batchSizes.add(bookIds.size());
			return Collections.emptyList();
		}
	}


	@Controller
	private static class InvalidReturnTypeController {

		@BatchMapping
		public void authors(List<Book> books) {
		}
	}


	@Controller
	private static class SchemaAndBatchMappingController {

		@BatchMapping
		@SchemaMapping
		public void authors(List<Book> books) {
		}
	}

}

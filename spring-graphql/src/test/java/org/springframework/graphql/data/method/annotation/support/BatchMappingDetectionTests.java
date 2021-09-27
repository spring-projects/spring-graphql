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

import java.util.List;
import java.util.Map;

import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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
 * Unit tests for {@link AnnotatedDataFetcherConfigurer}, focusing on detection
 * and mapping of handler methods to schema fields.
 *
 * @author Rossen Stoyanchev
 */
@SuppressWarnings({"rawtypes", "unused"})
public class BatchMappingDetectionTests {

	private final DefaultBatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();


	@Test
	void registerWithDefaultCoordinates() {

		Map<String, Map<String, DataFetcher>> map =
				initRuntimeWiringBuilder(BookController.class).build().getDataFetchers();

		assertThat(map).containsOnlyKeys("Book");
		assertThat(map.get("Book")).containsOnlyKeys(
				"authorFlux", "authorList", "authorMonoMap", "authorMap", "authorEnvironment");

		DataLoaderRegistry registry = new DataLoaderRegistry();
		this.batchLoaderRegistry.registerDataLoaders(registry);
		assertThat(registry.getDataLoadersMap()).containsOnlyKeys(
				"Book.authorFlux", "Book.authorList", "Book.authorMonoMap", "Book.authorMap", "Book.authorEnvironment");
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

	private RuntimeWiring.Builder initRuntimeWiringBuilder(Class<?> handlerType) {
		AnnotationConfigApplicationContext appContext = new AnnotationConfigApplicationContext();
		appContext.registerBean(handlerType);
		appContext.registerBean(BatchLoaderRegistry.class, () -> this.batchLoaderRegistry);
		appContext.refresh();

		AnnotatedDataFetcherConfigurer configurer = new AnnotatedDataFetcherConfigurer();
		configurer.setApplicationContext(appContext);
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
		public List<Author> authorEnvironment(BatchLoaderEnvironment environment, List<Book> books) {
			return null;
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

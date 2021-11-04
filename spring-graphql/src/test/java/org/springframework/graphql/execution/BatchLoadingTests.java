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
package org.springframework.graphql.execution;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.ExecutionResult;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlTestUtils;
import org.springframework.graphql.RequestInput;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for requests with batch loading, performed through an
 * {@link ExecutionGraphQlService} configured with a {@link BatchLoaderRegistry}.
 *
 * @author Rossen Stoyanchev
 */
public class BatchLoadingTests {

	private final BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();


	@Test
	void batchLoader() {
		String query = "{ " +
				"  booksByCriteria(criteria: {author:\"Orwell\"}) { " +
				"    author {" +
				"      firstName, " +
				"      lastName " +
				"    }" +
				"  }" +
				"}";

		this.registry.forTypePair(Long.class, Author.class)
				.registerBatchLoader((ids, env) -> Flux.fromIterable(ids).map(BookSource::getAuthor));

		ExecutionGraphQlService service = initExecutionGraphQlService(wiring -> {
			wiring.type("Query", builder -> builder.dataFetcher("booksByCriteria", env -> {
				Map<String, Object> criteria = env.getArgument("criteria");
				String authorName = (String) criteria.get("author");
				return BookSource.findBooksByAuthor(authorName).stream()
						.map(book -> new Book(book.getId(), book.getName(), book.getAuthorId()))
						.collect(Collectors.toList());
			}));
			wiring.type("Book", builder -> builder.dataFetcher("author", env -> {
				Book book = env.getSource();
				DataLoader<Long, Author> dataLoader = env.getDataLoader(Author.class.getName());
				return dataLoader.load(book.getAuthorId());
			}));
		});

		ExecutionResult result = service.execute(new RequestInput(query, null, null, null)).block();

		assertThat(result.getErrors()).isEmpty();
		Map<String, Object> data = result.getData();
		assertThat(data).isNotNull();

		List<Map<String, Object>> bookList = getValue(data, "booksByCriteria");
		assertThat(bookList).hasSize(2);
		Map<String, Object> authorMap = (Map<String, Object>) bookList.get(0).get("author");
		assertThat(authorMap).isNotNull();
		assertThat(authorMap).containsEntry("firstName", "George");
		assertThat(authorMap).containsEntry("lastName", "Orwell");
	}

	private ExecutionGraphQlService initExecutionGraphQlService(RuntimeWiringConfigurer configurer) {
		GraphQlSource source = GraphQlTestUtils.graphQlSource(BookSource.schema, configurer).build();
		ExecutionGraphQlService service = new ExecutionGraphQlService(source);
		service.addDataLoaderRegistrar(this.registry);
		return service;
	}

	@SuppressWarnings("unchecked")
	private <T> T getValue(Map<String, Object> data, String key) {
		return (T) data.get(key);
	}

}

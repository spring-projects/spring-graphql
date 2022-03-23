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
package org.springframework.graphql.execution;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.TestExecutionRequest;

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
		String document = "{ " +
				"  booksByCriteria(criteria: {author:\"Orwell\"}) { " +
				"    author {" +
				"      firstName, " +
				"      lastName " +
				"    }" +
				"  }" +
				"}";

		this.registry.forTypePair(Long.class, Author.class)
				.registerBatchLoader((ids, env) -> Flux.fromIterable(ids).map(BookSource::getAuthor));

		ExecutionGraphQlService service = GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("booksByCriteria", env -> {
					Map<String, Object> criteria = env.getArgument("criteria");
					String authorName = (String) criteria.get("author");
					return BookSource.findBooksByAuthor(authorName).stream()
							.map(book -> new Book(book.getId(), book.getName(), book.getAuthorId()))
							.collect(Collectors.toList());
				})
				.dataFetcher("Book", "author", env -> {
					Book book = env.getSource();
					DataLoader<Long, Author> dataLoader = env.getDataLoader(Author.class.getName());
					return dataLoader.load(book.getAuthorId());
				})
				.dataLoaders(this.registry)
				.toGraphQlService();

		Mono<ExecutionGraphQlResponse> responseMono = service.execute(TestExecutionRequest.forDocument(document));

		List<Book> books = ResponseHelper.forResponse(responseMono).toList("booksByCriteria", Book.class);
		assertThat(books).hasSize(2);

		Author author = books.get(0).getAuthor();
		assertThat(author).isNotNull();
		assertThat(author.getFirstName()).isEqualTo("George");
		assertThat(author.getLastName()).isEqualTo("Orwell");
	}

}

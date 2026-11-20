/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import graphql.ErrorType;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultExecutionGraphQlServiceTests {

	@Test
	void customDataLoaderRegistry() {
		GraphQlSource graphQlSource = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "hi")
				.toGraphQlSource();

		BatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();
		DefaultExecutionGraphQlService graphQlService = new DefaultExecutionGraphQlService(graphQlSource);
		graphQlService.addDataLoaderRegistrar(batchLoaderRegistry);

		// gh-1020: register loader after adding the registry to DefaultExecutionGraphQlService
		batchLoaderRegistry.forTypePair(Book.class, Author.class)
				.registerBatchLoader((books, batchLoaderEnvironment) -> Flux.empty());

		DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();

		ExecutionGraphQlRequest request = TestExecutionRequest.forDocument("{ greeting }");
		request.configureExecutionInput((input, builder) -> builder.dataLoaderRegistry(dataLoaderRegistry).build());

		ExecutionGraphQlResponse response = graphQlService.execute(request).block();
		Map<?, ?> data = response.getExecutionResult().getData();
		assertThat(data).isEqualTo(Map.of("greeting", "hi"));
		assertThat(dataLoaderRegistry.getDataLoaders()).hasSize(1);
	}

	@Test
	void shouldHandleGraphQlErrors() {
		ExecutionGraphQlResponse response = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "hi")
				.toGraphQlService()
				.execute(new DefaultExecutionGraphQlRequest("{ greeting }", "unknown", null, null, "uniqueId", null))
				.block();

		assertThat(response.getExecutionResult().getErrors()).singleElement()
				.hasFieldOrPropertyWithValue("errorType", ErrorType.ValidationError);
	}

	@Test
	void cancellationSupport() throws Exception {
		AtomicBoolean called = new AtomicBoolean();
		Mono<Book> bookMono = Mono.just(BookSource.getBookWithoutAuthor(1L))
				.delayElement(Duration.ofSeconds(1));

		Mono<ExecutionGraphQlResponse> execution = GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", (env) -> bookMono)
				.dataFetcher("Book", "author", (env) -> {
					called.set(true);
					return BookSource.getAuthor(1L);
				})
				.toGraphQlService()

				.execute(TestExecutionRequest.forDocument("{ bookById(id: 1) { author { firstName } } }"));

		StepVerifier.create(execution).thenAwait(Duration.ofMillis(500)).thenCancel().verify();
		Thread.sleep(1000);
		assertThat(called).isFalse();
	}

}

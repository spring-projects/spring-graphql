/*
 * Copyright 2002-2025 the original author or authors.
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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import graphql.ErrorType;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
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
public class DefaultExecutionGraphQlServiceTests {

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
	@Disabled("until https://github.com/spring-projects/spring-graphql/issues/1171")
	void cancellationSupport() {
		AtomicBoolean cancelled = new AtomicBoolean();
		Mono<String> greetingMono = Mono.just("hi")
				.delayElement(Duration.ofSeconds(3))
				.doOnCancel(() -> cancelled.set(true));

		Mono<ExecutionGraphQlResponse> execution = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> greetingMono)
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument("{ greeting }"));

		StepVerifier.create(execution).thenCancel().verify();
		assertThat(cancelled).isTrue();
	}

}

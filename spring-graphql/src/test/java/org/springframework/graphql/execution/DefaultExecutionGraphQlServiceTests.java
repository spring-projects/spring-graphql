/*
 * Copyright 2002-2023 the original author or authors.
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

import java.util.Map;

import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.graphql.Author;
import org.springframework.graphql.Book;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.TestExecutionRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.4
 */
public class DefaultExecutionGraphQlServiceTests {

	@Test
	void customDataLoaderRegistry() {
		DefaultBatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();
		batchLoaderRegistry.forTypePair(Book.class, Author.class)
				.registerBatchLoader((books, batchLoaderEnvironment) -> Flux.empty());

		GraphQlSource graphQlSource = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "hi")
				.toGraphQlSource();

		DefaultExecutionGraphQlService graphQlService = new DefaultExecutionGraphQlService(graphQlSource);
		graphQlService.addDataLoaderRegistrar(batchLoaderRegistry);

		DataLoaderRegistry myRegistry = new DataLoaderRegistry();

		ExecutionGraphQlRequest request = TestExecutionRequest.forDocument("{ greeting }");
		request.configureExecutionInput((input, builder) -> builder.dataLoaderRegistry(myRegistry).build());

		ExecutionGraphQlResponse response = graphQlService.execute(request).block();
		Map<?, ?> data = response.getExecutionResult().getData();
		assertThat(data).isEqualTo(Map.of("greeting", "hi"));
		assertThat(myRegistry.getDataLoaders()).hasSize(1);
	}

}

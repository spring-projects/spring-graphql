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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.graphql.GraphQlTestUtils;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ContextDataFetcherDecorator}.
 * @author Rossen Stoyanchev
 */
public class ContextDataFetcherDecoratorTests {

	@Test
	void monoDataFetcher() throws Exception {
		GraphQL graphQl = initGraphQl(
				"type Query { greeting: String }",
				"Query", "greeting", (env) -> Mono.deferContextual((context) -> {
					Object name = context.get("name");
					return Mono.delay(Duration.ofMillis(50)).map((aLong) -> "Hello " + name);
				}));

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ReactorContextManager.setReactorContext(Context.of("name", "007"), input);

		Map<String, Object> data = graphQl.executeAsync(input).get().getData();

		assertThat(data).hasSize(1).containsEntry("greeting", "Hello 007");
	}

	@Test
	void fluxDataFetcher() throws Exception {
		GraphQL graphQl = initGraphQl(
				"type Query { greetings: [String] }",
				"Query", "greetings",
				(env) -> Mono.delay(Duration.ofMillis(50))
						.flatMapMany((aLong) -> Flux.deferContextual((context) -> {
							String name = context.get("name");
							return Flux.just("Hi", "Bonjour", "Hola").map((s) -> s + " " + name);
						})));

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greetings }").build();
		ReactorContextManager.setReactorContext(Context.of("name", "007"), input);

		ExecutionResult result = graphQl.executeAsync(input).get();

		List<String> data = GraphQlTestUtils.getData(result, "greetings");
		assertThat(data).containsExactly("Hi 007", "Bonjour 007", "Hola 007");
	}

	@Test
	void fluxDataFetcherSubscription() throws Exception {
		GraphQL graphQl = initGraphQl(
				"type Query { greeting: String } type Subscription { greetings: String }",
				"Subscription", "greetings", (env) -> Mono.delay(Duration.ofMillis(50))
						.flatMapMany((aLong) -> Flux.deferContextual((context) -> {
							String name = context.get("name");
							return Flux.just("Hi", "Bonjour", "Hola").map((s) -> s + " " + name);
						})));

		ExecutionInput input = ExecutionInput.newExecutionInput().query("subscription { greetings }").build();
		ReactorContextManager.setReactorContext(Context.of("name", "007"), input);

		Publisher<String> publisher = graphQl.executeAsync(input).get().getData();

		List<String> actual = Flux.from(publisher).cast(ExecutionResult.class)
				.map((result) -> GraphQlTestUtils.<String>getData(result, "greetings"))
				.collectList()
				.block();

		assertThat(actual).containsExactly("Hi 007", "Bonjour 007", "Hola 007");
	}

	@Test
	void dataFetcherWithThreadLocalContext() {
		ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		nameThreadLocal.set("007");
		TestThreadLocalAccessor<String> accessor = new TestThreadLocalAccessor<>(nameThreadLocal);
		try {
			GraphQL graphQl = initGraphQl(
					"type Query { greeting: String }",
					"Query", "greeting", (env) -> "Hello " + nameThreadLocal.get());

			ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
			ContextView view = ReactorContextManager.extractThreadLocalValues(accessor, Context.empty());
			ReactorContextManager.setReactorContext(view, input);

			ExecutionResult result = Mono.delay(Duration.ofMillis(10))
					.flatMap((aLong) -> Mono.fromFuture(graphQl.executeAsync(input)))
					.block();

			Map<String, Object> data = GraphQlTestUtils.getData(result);
			assertThat(data).hasSize(1).containsEntry("greeting", "Hello 007");
		}
		finally {
			nameThreadLocal.remove();
		}
	}

	private static GraphQL initGraphQl(
			String schemaContent, String typeName, String fieldName, DataFetcher<?> fetcher) {

		return GraphQlTestUtils.graphQlSource(schemaContent, typeName, fieldName, fetcher)
				.build().graphQl();
	}

}

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
import java.util.Collections;
import java.util.function.BiFunction;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExceptionResolversExceptionHandler}.
 * @author Rossen Stoyanchev
 */
public class ExceptionResolversExceptionHandlerTests {

	@Test
	void resolveException() throws Exception {
		GraphQL graphQl = graphQl((ex, env) ->
				Mono.just(Collections.singletonList(
						GraphqlErrorBuilder.newError(env)
								.message("Resolved error: " + ex.getMessage())
								.errorType(ErrorType.BAD_REQUEST).build())));

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ExecutionResult result = graphQl.executeAsync(input).get();

		GraphQlResponse response = GraphQlResponse.from(result);
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting");
		assertThat(response.error(0).errorType()).isEqualTo("BAD_REQUEST");

		String greeting = response.rawValue("greeting");
		assertThat(greeting).isNull();
	}

	@Test
	void resolveExceptionWithReactorContext() throws Exception {
		GraphQL graphQl = graphQl((ex, env) ->
				Mono.deferContextual((view) -> Mono.just(Collections.singletonList(
						GraphqlErrorBuilder.newError(env)
								.message("Resolved error: " + ex.getMessage() + ", name=" + view.get("name"))
								.errorType(ErrorType.BAD_REQUEST).build()))));

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ReactorContextManager.setReactorContext(Context.of("name", "007"), input);

		ExecutionResult result = graphQl.executeAsync(input).get();

		GraphQlResponse response = GraphQlResponse.from(result);
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting, name=007");
	}

	@Test
	void resolveExceptionWithThreadLocal() {
		ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		nameThreadLocal.set("007");
		TestThreadLocalAccessor<String> accessor = new TestThreadLocalAccessor<>(nameThreadLocal);
		try {
			GraphQL graphQl = graphQl(threadLocalContextAwareResolver((ex, env) ->
					GraphqlErrorBuilder.newError(env)
							.message("Resolved error: " + ex.getMessage() + ", name=" + nameThreadLocal.get())
							.errorType(ErrorType.BAD_REQUEST)
							.build()));

			ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
			ContextView view = ReactorContextManager.extractThreadLocalValues(accessor, Context.empty());
			ReactorContextManager.setReactorContext(view, input);

			Mono<ExecutionResult> result = Mono.delay(Duration.ofMillis(10))
					.flatMap((aLong) -> Mono.fromFuture(graphQl.executeAsync(input)));

			GraphQlResponse response = GraphQlResponse.from(result);
			assertThat(response.errorCount()).isEqualTo(1);
			assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting, name=007");
		}
		finally {
			nameThreadLocal.remove();
		}
	}

	@Test
	void unresolvedException() throws Exception {
		GraphQL graphQl = graphQl((exception, environment) -> Mono.empty());

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ExecutionResult result = graphQl.executeAsync(input).get();

		GraphQlResponse response = GraphQlResponse.from(result);
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).isEqualTo("Invalid greeting");
		assertThat(response.error(0).errorType()).isEqualTo("INTERNAL_ERROR");

		String greeting = response.rawValue("greeting");
		assertThat(greeting).isNull();
	}

	@Test
	void suppressedException() throws Exception {
		GraphQL graphQl = graphQl((ex, env) -> Mono.just(Collections.emptyList()));

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ExecutionResult result = graphQl.executeAsync(input).get();

		String greeting = GraphQlResponse.from(result).rawValue("greeting");
		assertThat(greeting).isNull();
	}

	private static GraphQL graphQl(DataFetcherExceptionResolver exceptionResolver) {
		return GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> {
					throw new IllegalArgumentException("Invalid greeting");
				})
				.exceptionResolver(exceptionResolver)
				.toGraphQl();
	}

	private static DataFetcherExceptionResolver threadLocalContextAwareResolver(
			BiFunction<Throwable, DataFetchingEnvironment, GraphQLError> resolver) {

		DataFetcherExceptionResolverAdapter adapter = new DataFetcherExceptionResolverAdapter() {

			@Override
			protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
				return resolver.apply(ex, env);
			}
		};
		adapter.setThreadLocalContextAware(true);
		return adapter;
	}

}

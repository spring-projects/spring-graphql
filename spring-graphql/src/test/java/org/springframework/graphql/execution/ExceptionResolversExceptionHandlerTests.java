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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExceptionResolversExceptionHandler}.
 */
public class ExceptionResolversExceptionHandlerTests {

	@Test
	void resolveException() throws Exception {
		GraphQL graphQl = graphQl("type Query { greeting: String }",
				"Query", "greeting", env -> {
					throw new IllegalArgumentException("Invalid greeting");
				},
				new SingleErrorExceptionResolver() {
					@Override
					protected Mono<GraphQLError> doResolve(Throwable ex, DataFetchingEnvironment env) {
						return Mono.deferContextual(view ->
								Mono.just(GraphqlErrorBuilder.newError(env)
										.message("Resolved error: " + ex.getMessage() + ", name=" + view.get("name"))
										.errorType(ErrorType.BAD_REQUEST)
										.build()));
					}});

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ContextManager.setReactorContext(Context.of("name", "007"), input);

		ExecutionResult result = graphQl.executeAsync(input).get();

		Map<String, Object> data = result.getData();
		assertThat(data).hasSize(1).containsEntry("greeting", null);

		List<GraphQLError> errors = result.getErrors();
		assertThat(errors).hasSize(1);
		GraphQLError error = errors.get(0);
		assertThat(error.getMessage()).isEqualTo("Resolved error: Invalid greeting, name=007");
		assertThat(error.getErrorType().toString()).isEqualTo("BAD_REQUEST");
	}

	@Test
	void unresolvedException() throws Exception {
		GraphQL graphQl = graphQl("type Query { greeting: String }",
				"Query", "greeting", env -> {
					throw new IllegalArgumentException("Invalid greeting");
				},
				(exception, environment) -> Mono.empty());

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ExecutionResult result = graphQl.executeAsync(input).get();

		Map<String, Object> data = result.getData();
		assertThat(data).hasSize(1).containsEntry("greeting", null);

		List<GraphQLError> errors = result.getErrors();
		assertThat(errors).hasSize(1);
		GraphQLError error = errors.get(0);
		assertThat(error.getMessage()).isEqualTo("Invalid greeting");
		assertThat(error.getErrorType().toString()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void suppressedException() throws Exception {
		GraphQL graphQl = graphQl("type Query { greeting: String }",
				"Query", "greeting", env -> {
					throw new IllegalArgumentException("Invalid greeting");
				},
				(ex, env) -> Mono.just(Collections.emptyList()));

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ExecutionResult result = graphQl.executeAsync(input).get();

		Map<String, Object> data = result.getData();
		assertThat(data).hasSize(1).containsEntry("greeting", null);
		assertThat(result.getErrors()).hasSize(0);
	}

	private GraphQL graphQl(String schemaContent,
			String typeName, String fieldName, DataFetcher<?> dataFetcher,
			DataFetcherExceptionResolver... resolvers) {

		RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
				.type(typeName, builder -> builder.dataFetcher(fieldName, dataFetcher))
				.build();

		return GraphQlSource.builder()
				.schemaResource(new ByteArrayResource(schemaContent.getBytes(StandardCharsets.UTF_8)))
				.runtimeWiring(wiring)
				.exceptionResolvers(Arrays.asList(resolvers))
				.build()
				.graphQl();
	}

}

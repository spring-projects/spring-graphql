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

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ContextDataFetcherDecorator}.
 * @author Rossen Stoyanchev
 */
public class ContextDataFetcherDecoratorTests {

	private static final String SCHEMA_CONTENT =
			"type Query { greeting: String, greetings: [String] } type Subscription { greetings: String }";


	@Test
	void monoDataFetcher() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greeting", (env) ->
						Mono.deferContextual((context) -> {
							Object name = context.get("name");
							return Mono.delay(Duration.ofMillis(50)).map((aLong) -> "Hello " + name);
						}))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ReactorContextManager.setReactorContext(Context.of("name", "007"), input.getGraphQLContext());

		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		String greeting = ResponseHelper.forResult(executionResult).toEntity("greeting", String.class);
		assertThat(greeting).isEqualTo("Hello 007");
	}

	@Test
	void fluxDataFetcher() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greetings", (env) ->
						Mono.delay(Duration.ofMillis(50))
								.flatMapMany((aLong) -> Flux.deferContextual((context) -> {
									String name = context.get("name");
									return Flux.just("Hi", "Bonjour", "Hola").map((s) -> s + " " + name);
								})))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greetings }").build();
		ReactorContextManager.setReactorContext(Context.of("name", "007"), input.getGraphQLContext());

		ExecutionResult result = graphQl.executeAsync(input).get();

		List<String> data = ResponseHelper.forResult(result).toList("greetings", String.class);
		assertThat(data).containsExactly("Hi 007", "Bonjour 007", "Hola 007");
	}

	@Test
	void fluxDataFetcherSubscription() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.subscriptionFetcher("greetings", (env) ->
						Mono.delay(Duration.ofMillis(50))
								.flatMapMany((aLong) -> Flux.deferContextual((context) -> {
									String name = context.get("name");
									return Flux.just("Hi", "Bonjour", "Hola").map((s) -> s + " " + name);
								})))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("subscription { greetings }").build();
		ReactorContextManager.setReactorContext(Context.of("name", "007"), input.getGraphQLContext());

		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		Flux<String> greetingsFlux = ResponseHelper.forSubscription(executionResult)
				.map(response -> response.toEntity("greetings", String.class));

		StepVerifier.create(greetingsFlux)
				.expectNext("Hi 007", "Bonjour 007", "Hola 007")
				.verifyComplete();
	}

	@Test
	void fluxDataFetcherSubscriptionThrowException() throws Exception {

		SubscriptionExceptionResolver resolver =
				SubscriptionExceptionResolver.forSingleError(exception ->
						GraphqlErrorBuilder.newError()
								.message("Error: " + exception.getMessage())
								.errorType(ErrorType.BAD_REQUEST)
								.extensions(Collections.singletonMap("a", "b"))
								.build());

		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.subscriptionExceptionResolvers(resolver)
				.subscriptionFetcher("greetings",
						(env) -> Mono.delay(Duration.ofMillis(50))
								.handle((aLong, sink) -> {
									sink.next("Hi!");
									sink.error(new RuntimeException("Example Error"));
								}))
				.toGraphQl();

		String query = "subscription { greetings }";
		ExecutionInput input = ExecutionInput.newExecutionInput().query(query).build();
		ExecutionResult result = graphQl.executeAsync(input).get();

		Flux<String> flux = ResponseHelper.forSubscription(result)
				.map(message -> message.toEntity("greetings", String.class));

		StepVerifier.create(flux)
				.expectNext("Hi!")
				.expectErrorSatisfies(ex -> {
					List<GraphQLError> errors = ((SubscriptionPublisherException) ex).getErrors();
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getMessage()).isEqualTo("Error: Example Error");
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
					assertThat(errors.get(0).getExtensions()).isEqualTo(Collections.singletonMap("a", "b"));

				})
				.verify();
	}

	@Test
	void dataFetcherWithThreadLocalContext() {
		ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		nameThreadLocal.set("007");
		TestThreadLocalAccessor<String> accessor = new TestThreadLocalAccessor<>(nameThreadLocal);
		try {
			GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
					.queryFetcher("greeting", (env) -> "Hello " + nameThreadLocal.get())
					.toGraphQl();

			ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
			ContextView view = ReactorContextManager.extractThreadLocalValues(accessor, Context.empty());
			ReactorContextManager.setReactorContext(view, input.getGraphQLContext());

			Mono<ExecutionResult> resultMono = Mono.delay(Duration.ofMillis(10))
					.flatMap((aLong) -> Mono.fromFuture(graphQl.executeAsync(input)));

			String greeting = ResponseHelper.forResult(resultMono).toEntity("greeting", String.class);
			assertThat(greeting).isEqualTo("Hello 007");
		}
		finally {
			nameThreadLocal.remove();
		}
	}

}

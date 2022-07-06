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

import graphql.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ContextDataFetcherDecorator}.
 * @author Rossen Stoyanchev
 */
public class ContextDataFetcherDecoratorTests {

	@Test
	void monoDataFetcher() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent("type Query { greeting: String }")
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
		GraphQL graphQl = GraphQlSetup.schemaContent("type Query { greetings: [String] }")
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
		GraphQL graphQl = GraphQlSetup.schemaContent("type Query { greeting: String } type Subscription { greetings: String }")
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
		GraphQLError expectedError = GraphqlErrorBuilder.newError()
				.message("Error: Example Error")
				.errorType(ErrorType.INTERNAL_ERROR)
				.extensions(Collections.singletonMap("a", "b"))
				.build();

		SubscriptionExceptionResolver subscriptionSingleExceptionResolverAdapter = Mockito.spy(
				new SubscriptionExceptionResolverAdapter() {
					@Override
					protected GraphQLError resolveToSingleError(Throwable exception) {
						return GraphqlErrorBuilder.newError()
								.message("Error: " + exception.getMessage())
								.errorType(ErrorType.INTERNAL_ERROR)
								.extensions(Collections.singletonMap("a", "b"))
								.build();
					}
				}
		);

		GraphQL graphQl = GraphQlSetup.schemaContent("type Query { greeting: String } type Subscription { greetings: String }")
				.subscriptionExceptionResolvers(subscriptionSingleExceptionResolverAdapter)
				.subscriptionFetcher("greetings", (env) ->
						Mono.delay(Duration.ofMillis(50))
								.flatMapMany((aLong) -> Flux.create(sink -> {
									sink.next("Hi!");
									sink.error(new RuntimeException("Example Error"));
								})))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("subscription { greetings }").build();

		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		Flux<String> greetingsFlux = ResponseHelper.forSubscription(executionResult)
						.map(message -> message.toEntity("greetings", String.class));

		StepVerifier.create(greetingsFlux)
				.expectNext("Hi!")
				.expectErrorSatisfies(error -> assertThat(error)
						.usingRecursiveComparison()
						.isEqualTo(new SubscriptionStreamException(Collections.singletonList(expectedError))))
				.verify();

		verify(subscriptionSingleExceptionResolverAdapter).resolveException(any(RuntimeException.class));
	}

	@Test
	void monoDataFetcherSubscriptionThrowException() throws Exception {
		GraphQLError expectedError = GraphqlErrorBuilder.newError()
				.message("Error: Example Error")
				.errorType(ErrorType.INTERNAL_ERROR)
				.extensions(Collections.singletonMap("a", "b"))
				.build();

		SubscriptionExceptionResolver subscriptionSingleExceptionResolverAdapter = Mockito.spy(
				new SubscriptionExceptionResolverAdapter() {
					@Override
					protected GraphQLError resolveToSingleError(Throwable exception) {
						return GraphqlErrorBuilder.newError()
								.message("Error: " + exception.getMessage())
								.errorType(ErrorType.INTERNAL_ERROR)
								.extensions(Collections.singletonMap("a", "b"))
								.build();
					}
				}
		);

		GraphQL graphQl = GraphQlSetup.schemaContent("type Query { greeting: String } type Subscription { greetings: String }")
				.subscriptionExceptionResolvers(subscriptionSingleExceptionResolverAdapter)
				.subscriptionFetcher("greetings", (env) ->
						Mono.delay(Duration.ofMillis(50))
								.then(Mono.error(new RuntimeException("Example Error"))))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("subscription { greetings }").build();

		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		Flux<ResponseHelper> greetingsFlux = ResponseHelper.forSubscription(executionResult);

		StepVerifier.create(greetingsFlux)
				.expectErrorSatisfies(error -> assertThat(error)
						.usingRecursiveComparison()
						.isEqualTo(new SubscriptionStreamException(Collections.singletonList(expectedError))))
				.verify();

		verify(subscriptionSingleExceptionResolverAdapter).resolveException(any(RuntimeException.class));
	}

	@Test
	void dataFetcherWithThreadLocalContext() {
		ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		nameThreadLocal.set("007");
		TestThreadLocalAccessor<String> accessor = new TestThreadLocalAccessor<>(nameThreadLocal);
		try {
			GraphQL graphQl = GraphQlSetup.schemaContent("type Query { greeting: String }")
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

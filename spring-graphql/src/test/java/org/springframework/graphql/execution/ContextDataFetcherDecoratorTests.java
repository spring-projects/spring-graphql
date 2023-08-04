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
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ContextDataFetcherDecorator}.
 * @author Rossen Stoyanchev
 */
@SuppressWarnings("ReactiveStreamsUnusedPublisher")
public class ContextDataFetcherDecoratorTests {

	private static final String SCHEMA_CONTENT = "" +
			"directive @UpperCase on FIELD_DEFINITION " +
			"type Query { " +
			"  greeting: String @UpperCase, " +
			"  greetings: [String] " +
			"} " +
			"type Subscription { " +
			"  greetings: String " +
			"}";


	@Test
	void monoDataFetcher() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greeting", env ->
						Mono.deferContextual(context -> {
							Object name = context.get("name");
							return Mono.delay(Duration.ofMillis(50)).map(aLong -> "Hello " + name);
						}))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		input.getGraphQLContext().put("name", "007");

		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		String greeting = ResponseHelper.forResult(executionResult).toEntity("greeting", String.class);
		assertThat(greeting).isEqualTo("Hello 007");
	}

	@Test
	void fluxDataFetcher() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greetings", env ->
						Mono.delay(Duration.ofMillis(50))
								.flatMapMany(aLong -> Flux.deferContextual(context -> {
									String name = context.get("name");
									return Flux.just("Hi", "Bonjour", "Hola").map(s -> s + " " + name);
								})))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greetings }").build();
		input.getGraphQLContext().put("name", "007");

		ExecutionResult result = graphQl.executeAsync(input).get();

		List<String> data = ResponseHelper.forResult(result).toList("greetings", String.class);
		assertThat(data).containsExactly("Hi 007", "Bonjour 007", "Hola 007");
	}

	@Test
	void fluxDataFetcherSubscription() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.subscriptionFetcher("greetings", env ->
						Mono.delay(Duration.ofMillis(50))
								.flatMapMany(aLong -> Flux.deferContextual(context -> {
									String name = context.get("name");
									return Flux.just("Hi", "Bonjour", "Hola").map(s -> s + " " + name);
								})))
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("subscription { greetings }").build();
		input.getGraphQLContext().put("name", "007");

		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		Flux<String> greetingsFlux = ResponseHelper.forSubscription(executionResult)
				.map(response -> response.toEntity("greetings", String.class));

		StepVerifier.create(greetingsFlux)
				.expectNext("Hi 007", "Bonjour 007", "Hola 007")
				.verifyComplete();
	}

	@Test
	void fluxDataFetcherSubscriptionThrowingException() throws Exception {

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
						env -> Mono.delay(Duration.ofMillis(50))
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
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("007");
		ContextRegistry.getInstance().registerThreadLocalAccessor(new TestThreadLocalAccessor<>(threadLocal));
		try {
			GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
					.queryFetcher("greeting", env -> "Hello " + threadLocal.get())
					.toGraphQl();

			ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
			ContextSnapshot.captureAll().updateContext(input.getGraphQLContext());

			Mono<ExecutionResult> resultMono = Mono.delay(Duration.ofMillis(10))
					.flatMap(aLong -> Mono.fromFuture(graphQl.executeAsync(input)));

			String greeting = ResponseHelper.forResult(resultMono).toEntity("greeting", String.class);
			assertThat(greeting).isEqualTo("Hello 007");
		}
		finally {
			threadLocal.remove();
		}
	}

	@Test // gh-440
	void dataFetcherDecoratedWithDataFetcherFactories() {

		SchemaDirectiveWiring directiveWiring = new SchemaDirectiveWiring() {

			@SuppressWarnings("unchecked")
			@Override
			public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {
				if (env.getDirective("UpperCase") != null) {
					return env.setFieldDataFetcher(DataFetcherFactories.wrapDataFetcher(
							env.getFieldDataFetcher(),
							((dataFetchingEnv, value) -> {
								if (value instanceof String) {
									return ((String) value).toUpperCase();
								}
								else if (value instanceof Mono) {
									return ((Mono<String>) value).map(String::toUpperCase);
								}
								else {
									throw new IllegalArgumentException();
								}
							})));
				}
				else {
					return env.getElement();
				}
			}
		};

		BiConsumer<SchemaDirectiveWiring, DataFetcher<?>> tester = (schemaDirectiveWiring, dataFetcher) -> {

			GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
					.queryFetcher("greeting", dataFetcher)
					.runtimeWiring(builder -> builder.directiveWiring(directiveWiring))
					.toGraphQl();

			ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
			Mono<ExecutionResult> resultMono = Mono.fromFuture(graphQl.executeAsync(input));

			String greeting = ResponseHelper.forResult(resultMono).toEntity("greeting", String.class);
			assertThat(greeting).isEqualTo("HELLO");
		};

		tester.accept(directiveWiring, env -> CompletableFuture.completedFuture("hello"));
		tester.accept(directiveWiring, env -> Mono.just("hello"));
	}

}

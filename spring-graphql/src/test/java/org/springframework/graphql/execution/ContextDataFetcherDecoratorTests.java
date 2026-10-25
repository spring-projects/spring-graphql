/*
 * Copyright 2002-present the original author or authors.
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

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.TrivialDataFetcher;
import graphql.execution.AbortExecutionException;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link ContextDataFetcherDecorator}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
@SuppressWarnings("ReactiveStreamsUnusedPublisher")
class ContextDataFetcherDecoratorTests {

	private static final String SCHEMA_CONTENT = """
			directive @UpperCase on FIELD_DEFINITION \
			type Query { \
				greeting: String @UpperCase, \
				greetings: [String] \
			} \
			type Subscription { \
				greetings: String \
			}""";

	private static final String CONTAINER_SCHEMA = """
			type Query { \
				container: Container \
			} \
			type Container { \
				value: String \
			}""";


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
		input.getGraphQLContext().put("name", "007");

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
		input.getGraphQLContext().put("name", "007");

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
		input.getGraphQLContext().put("name", "007");

		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		Flux<String> greetingsFlux = ResponseHelper.forSubscription(executionResult)
				.map(response -> response.toEntity("greetings", String.class));

		StepVerifier.create(greetingsFlux)
				.expectNext("Hi 007", "Bonjour 007", "Hola 007")
				.verifyComplete();
	}

	@Test
	void fluxDataFetcherSubscriptionWithDataFetcherResult() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.subscriptionFetcher("greetings", (env) -> {
					Flux<String> flux = Mono.delay(Duration.ofMillis(50))
							.flatMapMany((aLong) -> Flux.deferContextual((context) -> {
								String name = context.get("name");
								return Flux.just("Hi", "Bonjour", "Hola").map((s) -> s + " " + name);
							}));
					return DataFetcherResult.newResult().data(flux).build();
				})
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
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("007");
		ContextRegistry.getInstance().registerThreadLocalAccessor(new TestThreadLocalAccessor<>(threadLocal));
		try {
			GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
					.queryFetcher("greeting", (env) -> "Hello " + threadLocal.get())
					.toGraphQl();

			ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
			ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();
			snapshot.updateContext(input.getGraphQLContext());

			Mono<ExecutionResult> resultMono = Mono.delay(Duration.ofMillis(10))
					.flatMap((aLong) -> Mono.fromFuture(graphQl.executeAsync(input)));

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
				if (env.getAppliedDirective("UpperCase") != null) {
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


	@Test
	void completableFutureCompletedOnAnotherThread() throws Exception {
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("007");
		ContextRegistry.getInstance().registerThreadLocalAccessor(new TestThreadLocalAccessor<>(threadLocal));
		try {
			CountDownLatch fetcherReturned = new CountDownLatch(1);
			CompletableFuture<Container> future = new CompletableFuture<>();

			// TrivialDataFetcher is not decorated (see gh-980), so it observes ThreadLocals
			// only if completion of the parent field's future propagated them.
			DataFetcher<String> childFetcher = (TrivialDataFetcher<String>) (env) -> threadLocal.get();

			GraphQL graphQl = GraphQlSetup.schemaContent(CONTAINER_SCHEMA)
					.queryFetcher("container", (env) -> {
						fetcherReturned.countDown();
						return future;
					})
					.runtimeWiring(builder -> builder.type("Container",
							type -> type.dataFetcher("value", childFetcher)))
					.toGraphQl();

			ExecutionInput input = ExecutionInput.newExecutionInput()
					.query("{ container { value } }").build();
			ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();
			snapshot.updateContext(input.getGraphQLContext());

			Mono<ExecutionResult> resultMono = Mono.delay(Duration.ofMillis(10))
					.flatMap((aLong) -> Mono.fromFuture(graphQl.executeAsync(input)));

			// Complete only after the fetcher has returned the future, so the completion
			// really happens on a different thread while execution is suspended
			Thread completer = new Thread(() -> {
				try {
					// Wait until the fetcher returned the future, then let the execution
					// strategy register its continuation on the wrapped future, so the
					// completion (and its dependents) run on this thread
					fetcherReturned.await();
					Thread.sleep(200);
				}
				catch (InterruptedException ex) {
					throw new IllegalStateException(ex);
				}
				future.complete(new Container("ignored"));
			});
			completer.start();

			String value = ResponseHelper.forResult(resultMono).toEntity("container.value", String.class);
			assertThat(value).isEqualTo("007");
		}
		finally {
			threadLocal.remove();
		}
	}

	@Test
	void completableFutureCompletedExceptionallyReachesResolver() throws Exception {
		CompletableFuture<String> gate = new CompletableFuture<>();
		CompletableFuture<String> future = gate.thenApply((value) -> {
			throw new IllegalStateException("boom");
		});
		AtomicReference<Throwable> resolved = new AtomicReference<>();
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greeting", (env) -> future)
				.exceptionResolver((exception, environment) -> {
					resolved.set(exception);
					return Mono.empty();
				})
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		Mono<ExecutionResult> resultMono = Mono.fromFuture(graphQl.executeAsync(input));

		// Failing the dependent stage produces a CompletionException, which must be
		// unwrapped before it reaches a resolver, as MonoCompletionStage unwrapped it
		Thread completer = new Thread(() -> gate.complete("go"));
		completer.start();

		ExecutionResult result = resultMono.toFuture().get(10, TimeUnit.SECONDS);
		assertThat(result.getErrors()).hasSize(1);
		await().untilAsserted(() -> assertThat(resolved.get())
				.isInstanceOf(IllegalStateException.class).hasMessage("boom"));
	}

	@Test // gh-980
	void trivialDataFetcherIsNotDecorated() {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greeting", (TrivialDataFetcher<?>) env -> "hello")
				.toGraphQl();

		GraphQLSchema schema = graphQl.getGraphQLSchema();
		FieldCoordinates coordinates = FieldCoordinates.coordinates("Query", "greeting");
		GraphQLFieldDefinition fieldDefinition = schema.getFieldDefinition(coordinates);
		DataFetcher<?> dataFetcher = schema.getCodeRegistry().getDataFetcher(coordinates, fieldDefinition);

		assertThat(dataFetcher).isInstanceOf(TrivialDataFetcher.class);
	}

	@Test
	void returnAbortExecutionForBlockingDataFetcherWhenRequestCancelled() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greeting", (env) -> "Hello")
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		input.cancel();
		ExecutionResult result = graphQl.executeAsync(input).get();

		assertThat(result.getErrors()).hasSize(1);
		assertThat(result.getErrors().get(0)).isInstanceOf(AbortExecutionException.class)
				.extracting("message").asString().isEqualTo("Execution has been asked to be cancelled");
	}

	@Test
	void cancelFluxDataFetcherSubscriptionWhenRequestCancelled() throws Exception {
		AtomicBoolean dataFetcherCancelled = new AtomicBoolean();
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.subscriptionFetcher("greetings", (env) ->
						Flux.just("Hi", "Bonjour", "Hola")
								.delayElements(Duration.ofSeconds(1))
								.doOnCancel(() -> dataFetcherCancelled.set(true))
						)
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("subscription { greetings }").build();
		ExecutionResult executionResult = graphQl.executeAsync(input).get();
		input.cancel();
		StepVerifier.create(ResponseHelper.forSubscription(executionResult))
				.verifyError(AbortExecutionException.class);
		await().atMost(Duration.ofSeconds(2)).until(dataFetcherCancelled::get);
		assertThat(dataFetcherCancelled).isTrue();
	}

	@Test
	void testExtensionsAreRetained() throws Exception {
		GraphQL graphQl = GraphQlSetup.schemaContent(SCHEMA_CONTENT)
				.queryFetcher("greeting", (env) ->
						DataFetcherResult.newResult().data("Hello")
								.extensions(Map.of("foo", "bar")).build())
				.toGraphQl();

		ExecutionInput input = ExecutionInput.newExecutionInput().query("{ greeting }").build();
		ExecutionResult executionResult = graphQl.executeAsync(input).get();

		String greeting = ResponseHelper.forResult(executionResult).toEntity("greeting", String.class);
		assertThat(greeting).isEqualTo("Hello");

		assertThat(executionResult.getExtensions()).containsEntry("foo", "bar");
	}

	@Test
	void completableFutureCancellationPropagatesToSource() throws Exception {
		CompletableFuture<String> source = new CompletableFuture<>();
		Object wrapped = newDecorator((env) -> source).get(environment(GraphQLContext.getDefault()));

		assertThat(wrapped).isInstanceOf(CompletableFuture.class);
		((CompletableFuture<?>) wrapped).cancel(true);

		assertThat(source).isCancelled();
	}

	@Test
	void completableFutureUnwrapsCompletionException() throws Exception {
		CompletableFuture<String> gate = new CompletableFuture<>();
		CompletableFuture<String> source = gate.thenApply((value) -> {
			throw new IllegalStateException("boom");
		});
		Object wrapped = newDecorator((env) -> source).get(environment(GraphQLContext.getDefault()));

		gate.complete("go");

		Throwable failure = ((CompletableFuture<?>) wrapped).handle((value, throwable) -> throwable).join();
		assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage("boom");
	}

	@Test
	void alreadyFailedFutureIsUnwrapped() throws Exception {
		CompletableFuture<String> source = CompletableFuture.supplyAsync(() -> {
			throw new IllegalStateException("boom");
		});
		// Ensure the source has already failed before the decorator sees it; the failed
		// fast path must still unwrap rather than return the stage as-is
		try {
			source.join();
		}
		catch (Exception ignored) {
		}
		Object wrapped = newDecorator((env) -> source).get(environment(GraphQLContext.getDefault()));

		Throwable failure = ((CompletableFuture<?>) wrapped).handle((value, throwable) -> throwable).join();
		assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage("boom");
	}

	@Test
	void originalFailureIsPrimaryWhenScopeFails() throws Exception {
		IllegalStateException original = new IllegalStateException("original");
		CompletableFuture<String> source = new CompletableFuture<>();
		GraphQLContext graphQlContext = GraphQLContext.getDefault();
		ContextPropagationHelper.saveInstance(failingSnapshotFactory(), graphQlContext);

		Object wrapped = newDecorator((env) -> source).get(environment(graphQlContext));
		source.completeExceptionally(original);

		Throwable failure = ((CompletableFuture<?>) wrapped).handle((value, throwable) -> throwable).join();

		assertThat(failure).isSameAs(original);
		assertThat(failure.getSuppressed()).hasSize(1);
	}

	@Test
	void alreadyCompletedFutureIsReturnedAsIs() throws Exception {
		CompletableFuture<String> source = CompletableFuture.completedFuture("hello");
		Object wrapped = newDecorator((env) -> source).get(environment(GraphQLContext.getDefault()));

		assertThat(wrapped).isSameAs(source);
	}

	private static DataFetchingEnvironment environment(GraphQLContext graphQlContext) {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
				.graphQLContext(graphQlContext)
				.build();
	}

	private static ContextDataFetcherDecorator newDecorator(DataFetcher<?> delegate) {
		try {
			Constructor<ContextDataFetcherDecorator> constructor =
					ContextDataFetcherDecorator.class.getDeclaredConstructor(
							DataFetcher.class, boolean.class, SubscriptionExceptionResolver.class);
			constructor.setAccessible(true);
			return constructor.newInstance(delegate, false, new CompositeSubscriptionExceptionResolver(List.of()));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Factory whose snapshots fail in {@code setThreadLocals}. */
	private static ContextSnapshotFactory failingSnapshotFactory() {
		ContextSnapshot snapshot = new ContextSnapshot() {
			@Override
			public <C> C updateContext(C context) {
				return context;
			}

			@Override
			public <C> C updateContext(C context, Predicate<Object> predicate) {
				return context;
			}

			@Override
			public Scope setThreadLocals() {
				throw new IllegalStateException("scope failure");
			}

			@Override
			public Scope setThreadLocals(Predicate<Object> predicate) {
				throw new IllegalStateException("scope failure");
			}

			@Override
			public <T> Callable<T> wrap(Callable<T> callable) {
				return callable;
			}
		};
		return new ContextSnapshotFactory() {
			@Override
			public ContextSnapshot captureAll(Object... values) {
				return snapshot;
			}

			@Override
			public ContextSnapshot captureFrom(Object... values) {
				return snapshot;
			}

			@Override
			public ContextSnapshot.Scope setThreadLocalsFrom(Object context, String... keys) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private record Container(String value) {
	}

}

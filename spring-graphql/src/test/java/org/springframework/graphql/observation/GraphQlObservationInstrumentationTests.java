/*
 * Copyright 2020-2023 the original author or authors.
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

package org.springframework.graphql.observation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import graphql.GraphqlErrorBuilder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleSpanBuilder;
import io.micrometer.tracing.test.simple.SimpleTracer;
import io.micrometer.tracing.test.simple.TracerAssert;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlObservationInstrumentation}.
 *
 * @author Brian Clozel
 */
class GraphQlObservationInstrumentationTests {

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final GraphQlObservationInstrumentation instrumentation = new GraphQlObservationInstrumentation(this.observationRegistry);

	private final GraphQlSetup graphQlSetup = GraphQlSetup.schemaResource(BookSource.schema).instrumentation(this.instrumentation);


	@Test
	void instrumentGraphQlRequestWhenSuccess() {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document));
		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasLowCardinalityKeyValue("graphql.outcome", "SUCCESS")
				.hasHighCardinalityKeyValueWithKey("graphql.execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasNumberOfObservationsWithNameEqualTo("graphql.datafetcher", 1)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasLowCardinalityKeyValue("graphql.outcome", "SUCCESS")
				.hasLowCardinalityKeyValue("graphql.field.name", "bookById")
				.hasHighCardinalityKeyValue("graphql.field.path", "/bookById");
	}

	@Test
	void instrumentGraphQlRequestWhenInvalidRequest() {
		String document = "invalid";
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document));
		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasLowCardinalityKeyValue("graphql.outcome", "REQUEST_ERROR")
				.hasHighCardinalityKeyValueWithKey("graphql.execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasNumberOfObservationsWithNameEqualTo("graphql.datafetcher", 0);
	}

	@Test
	void instrumentMultipleDataFetcherOperations() {
		String document = """
				{
					bookById(id: 1) {
						author {
							firstName,
							lastName
						}
					}
				}
				""";
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.dataFetcher("Book", "author", env -> BookSource.getAuthor(101L))
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document));
		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasLowCardinalityKeyValue("graphql.outcome", "SUCCESS")
				.hasHighCardinalityKeyValueWithKey("graphql.execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasNumberOfObservationsWithNameEqualTo("graphql.datafetcher", 2);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasLowCardinalityKeyValue("graphql.outcome", "SUCCESS")
				.hasLowCardinalityKeyValue("graphql.field.name", "bookById")
				.hasHighCardinalityKeyValue("graphql.field.path", "/bookById");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasAnObservationWithAKeyValue("graphql.field.name", "author")
				.hasAnObservationWithAKeyValue("graphql.field.path", "/bookById/author");
	}

	@Test
	void instrumentGraphQlRequestWhenDataFetchingFailure() {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		DataFetcherExceptionResolver resolver =
				DataFetcherExceptionResolver.forSingleError((ex, env) ->
						GraphqlErrorBuilder.newError(env)
								.message("Resolved error: " + ex.getMessage())
								.errorType(ErrorType.BAD_REQUEST).build());
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.exceptionResolver(resolver)
				.queryFetcher("bookById", env ->
						CompletableFuture.failedStage(new IllegalStateException("book fetching failure")))
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document));
		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasLowCardinalityKeyValue("graphql.outcome", "REQUEST_ERROR")
				.hasHighCardinalityKeyValueWithKey("graphql.execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasNumberOfObservationsWithNameEqualTo("graphql.datafetcher", 1)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasLowCardinalityKeyValue("graphql.outcome", "ERROR")
				.hasLowCardinalityKeyValue("graphql.error.type", "IllegalStateException")
				.hasLowCardinalityKeyValue("graphql.field.name", "bookById")
				.hasHighCardinalityKeyValue("graphql.field.path", "/bookById");
	}

	@Test
	void propagatesContextBetweenObservations() {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(TestExecutionRequest.forDocument(document));
		ResponseHelper response = ResponseHelper.forResponse(responseMono);

		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasLowCardinalityKeyValue("graphql.outcome", "SUCCESS")
				.hasHighCardinalityKeyValueWithKey("graphql.execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasParentObservationContextMatching(context -> context instanceof ExecutionRequestObservationContext);
	}

	@Test
	void setIncomingObservationAsParent() {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		ExecutionGraphQlRequest graphQlRequest = TestExecutionRequest.forDocument(document);
		Observation incoming = Observation.start("incoming", ObservationRegistry.create());
		graphQlRequest.configureExecutionInput((input, builder) ->
				builder.graphQLContext(contextBuilder -> contextBuilder.of("micrometer.observation", incoming)).build());
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(graphQlRequest);
		ResponseHelper response = ResponseHelper.forResponse(responseMono);

		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasParentObservationEqualTo(incoming);
		incoming.stop();
	}

	@Test
	void inboundTracingInformationIsPropagated() {
		SimpleTracer simpleTracer = new SimpleTracer();
		String traceId = "traceId";
		TracingObservationHandler<ReceiverContext> tracingHandler = new PropagatingReceiverTracingObservationHandler<>(simpleTracer, new TestPropagator(simpleTracer, traceId));
		this.observationRegistry.observationConfig().observationHandler(tracingHandler);
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		ExecutionGraphQlRequest executionRequest = TestExecutionRequest.forDocument(document);
		executionRequest.configureExecutionInput((input, builder) ->
				builder.graphQLContext(context -> context.of(TestPropagator.TRACING_HEADER_NAME, traceId)).build());
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(executionRequest);
		ResponseHelper response = ResponseHelper.forResponse(responseMono);

		TracerAssert.assertThat(simpleTracer)
				.onlySpan()
				.hasNameEqualTo("graphql query")
				.hasKindEqualTo(Span.Kind.SERVER)
				.hasTag("graphql.operation", "query")
				.hasTag("graphql.outcome", "SUCCESS")
				.hasTagWithKey("graphql.execution.id");
	}

	static class TestPropagator implements Propagator {

		public static String TRACING_HEADER_NAME = "X-Test-Tracing";

		private final SimpleTracer tracer;

		private final String traceId;

		TestPropagator(SimpleTracer tracer, String traceId) {
			this.tracer = tracer;
			this.traceId = traceId;
		}

		@Override
		public List<String> fields() {
			return List.of(TRACING_HEADER_NAME);
		}

		@Override
		public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {
			setter.set(carrier, TRACING_HEADER_NAME, "traceId");
		}

		@Override
		public <C> Span.Builder extract(C carrier, Getter<C> getter) {
			String foo = getter.get(carrier, TRACING_HEADER_NAME);
			assertThat(foo).isEqualTo(this.traceId);
			return new SimpleSpanBuilder(this.tracer);
		}
	}
}

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

import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetcher;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.graphql.*;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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


	@ParameterizedTest
	@MethodSource("successDataFetchers")
	void instrumentGraphQlRequestWhenSuccess(DataFetcher<?> dataFetcher) {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", dataFetcher)
				.toGraphQlService()
				.execute(document);
		ResponseHelper response = ResponseHelper.forResponse(responseMono);

		String name = response.rawValue("bookById.name");
		assertThat(name).isEqualTo("Nineteen Eighty-Four");

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

	static Stream<Arguments> successDataFetchers() {
		DataFetcher<Book> bookDataFetcher = environment -> BookSource.getBookWithoutAuthor(1L);
		return Stream.of(
				Arguments.of(bookDataFetcher),
				Arguments.of(new AsyncDataFetcher<>(bookDataFetcher)),
				Arguments.of((DataFetcher<DataFetcherResult<?>>) environment ->
						DataFetcherResult.newResult().data(BookSource.getBookWithoutAuthor(1L)).build())
		);
	}

	@Test
	void instrumentGraphQlRequestWhenInvalidRequest() {
		String document = "invalid";
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(document);
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
				.execute(document);
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
				.hasHighCardinalityKeyValue("graphql.field.path", "/bookById")
				.hasParentObservationContextMatching(ExecutionRequestObservationContext.class::isInstance);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasAnObservationWithAKeyValue("graphql.field.name", "author")
				.hasAnObservationWithAKeyValue("graphql.field.path", "/bookById/author");
	}

	@ParameterizedTest
	@MethodSource("failureDataFetchers")
	void instrumentGraphQlRequestWhenDataFetchingFailure(DataFetcher<?> dataFetcher) {
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
				.execute(document);
		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		assertThat(response.error(0).message()).isEqualTo("Resolved error: book fetching failure");

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

	static Stream<Arguments> failureDataFetchers() {
		DataFetcher<Book> bookDataFetcher = environment -> {
			throw new IllegalStateException("book fetching failure");
		};
		return Stream.of(
				Arguments.of(bookDataFetcher),
				Arguments.of((DataFetcher<?>) environment ->
						CompletableFuture.failedStage(new IllegalStateException("book fetching failure")))
		);
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
		ExecutionGraphQlRequest request = TestExecutionRequest.forDocument(document);
		Observation incoming = Observation.start("incoming", ObservationRegistry.create());
		request.configureExecutionInput((input, builder) ->
				builder.graphQLContext(contextBuilder -> contextBuilder.of(ObservationThreadLocalAccessor.KEY, incoming)).build());
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(request);
		ResponseHelper.forResponse(responseMono);

		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasParentObservationEqualTo(incoming);
		incoming.stop();
	}

	@ParameterizedTest
	@MethodSource("successDataFetchers")
	void propagatesContextBetweenObservations(DataFetcher<?> dataFetcher) {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", dataFetcher)
				.toGraphQlService()
				.execute(document);
		ResponseHelper.forResponse(responseMono);

		TestObservationRegistryAssert.assertThat(this.observationRegistry).hasObservationWithNameEqualTo("graphql.request")
				.that().hasLowCardinalityKeyValue("graphql.outcome", "SUCCESS")
				.hasHighCardinalityKeyValueWithKey("graphql.execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasParentObservationContextMatching(ExecutionRequestObservationContext.class::isInstance);
	}

	@Test
	void currentObservationSetInDataFetcherContext() {
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
		DataFetcher<Book> bookDataFetcher = environment -> {
			assertThat(observationRegistry.getCurrentObservation().getContext())
					.isInstanceOf(ExecutionRequestObservationContext.class);
			return BookSource.getBookWithoutAuthor(1L);
		};
		DataFetcher<Author> authorDataFetcher = environment -> {
			assertThat(observationRegistry.getCurrentObservation().getContext())
					.isInstanceOf(DataFetcherObservationContext.class);
			return BookSource.getAuthor(101L);
		};

		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", bookDataFetcher)
				.dataFetcher("Book", "author", authorDataFetcher)
				.toGraphQlService()
				.execute(document);
		ResponseHelper.forResponse(responseMono);
	}

}

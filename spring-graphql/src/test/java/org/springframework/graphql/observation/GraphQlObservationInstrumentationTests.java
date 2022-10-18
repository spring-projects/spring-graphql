/*
 * Copyright 2020-2022 the original author or authors.
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

import java.util.concurrent.CompletableFuture;

import graphql.GraphqlErrorBuilder;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionRequest;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;

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
				.that().hasLowCardinalityKeyValue("outcome", "SUCCESS")
				.hasHighCardinalityKeyValueWithKey("execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasNumberOfObservationsWithNameEqualTo("graphql.datafetcher", 1)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasLowCardinalityKeyValue("outcome", "SUCCESS")
				.hasLowCardinalityKeyValue("field.name", "bookById")
				.hasHighCardinalityKeyValue("field.path", "/bookById");
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
				.that().hasLowCardinalityKeyValue("outcome", "REQUEST_ERROR")
				.hasHighCardinalityKeyValueWithKey("execution.id");

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
				.that().hasLowCardinalityKeyValue("outcome", "SUCCESS")
				.hasHighCardinalityKeyValueWithKey("execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasNumberOfObservationsWithNameEqualTo("graphql.datafetcher", 2);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasLowCardinalityKeyValue("outcome", "SUCCESS")
				.hasLowCardinalityKeyValue("field.name", "bookById")
				.hasHighCardinalityKeyValue("field.path", "/bookById");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasAnObservationWithAKeyValue("field.name", "author")
				.hasAnObservationWithAKeyValue("field.path", "/bookById/author");
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
				.that().hasLowCardinalityKeyValue("outcome", "REQUEST_ERROR")
				.hasHighCardinalityKeyValueWithKey("execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasNumberOfObservationsWithNameEqualTo("graphql.datafetcher", 1)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasLowCardinalityKeyValue("outcome", "ERROR")
				.hasLowCardinalityKeyValue("error.type", "IllegalStateException")
				.hasLowCardinalityKeyValue("field.name", "bookById")
				.hasHighCardinalityKeyValue("field.path", "/bookById");
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
				.that().hasLowCardinalityKeyValue("outcome", "SUCCESS")
				.hasHighCardinalityKeyValueWithKey("execution.id");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.hasObservationWithNameEqualTo("graphql.datafetcher")
				.that()
				.hasParentObservationContextMatching(context -> context instanceof ExecutionRequestObservationContext);
	}



}
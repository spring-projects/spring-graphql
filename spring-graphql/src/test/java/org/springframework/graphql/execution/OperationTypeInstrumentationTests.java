/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;


import java.util.Set;

import graphql.language.OperationDefinition;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OperationTypeInstrumentation}.
 * @author Brian Clozel
 */
class OperationTypeInstrumentationTests {

	private final OperationTypeInstrumentation instrumentation = new OperationTypeInstrumentation();

	private final GraphQlSetup graphQlSetup = GraphQlSetup.schemaResource(BookSource.schema).instrumentation(this.instrumentation);

	@Test
	void allOperationTypesAllowedByDefault() {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		ExecutionGraphQlRequest request = TestExecutionRequest.forDocument(document);
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(request);
		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		assertThat(response.errorCount()).isZero();
	}

	@Test
	void rejectsWhenOperationNotAllowed() {
		String document = """
				{
					bookById(id: 1) {
						name
					}
				}
				""";
		ExecutionGraphQlRequest request = TestExecutionRequest.forDocument(document);
		request.allowedOperations(Set.of(OperationDefinition.Operation.MUTATION, OperationDefinition.Operation.SUBSCRIPTION));
		Mono<ExecutionGraphQlResponse> responseMono = graphQlSetup
				.queryFetcher("bookById", env -> BookSource.getBookWithoutAuthor(1L))
				.toGraphQlService()
				.execute(request);
		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		assertThat(response.errorCount()).isEqualTo(1);
		ResponseHelper.Error error = response.error(0);
		assertThat(error.errorType()).isEqualTo("OperationNotSupported");
		assertThat(error.message()).isEqualTo("Operation type 'QUERY' is not allowed for this request");
	}

}

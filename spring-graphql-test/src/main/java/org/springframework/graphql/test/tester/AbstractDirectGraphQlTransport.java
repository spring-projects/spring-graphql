/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;

import java.util.List;

import graphql.ExecutionResult;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.test.util.AssertionErrors;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.IdGenerator;

/**
 * Abstract base class for a {@link GraphQlTransport} that makes a direct call
 * to a server-side GraphQL handler or service.
 *
 * @author Rossen Stoyanchev
 */
abstract class AbstractDirectGraphQlTransport implements GraphQlTransport {

	protected static final IdGenerator idGenerator = new AlternativeJdkIdGenerator();


	@Override
	public Mono<GraphQlResponse> execute(GraphQlRequest request) {
		return executeInternal(toExecutionRequest(request)).cast(GraphQlResponse.class);
	}

	@SuppressWarnings({"ConstantConditions", "unchecked"})
	@Override
	public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
		return executeInternal(toExecutionRequest(request)).flatMapMany((response) -> {
			try {
				Object data = response.getData();
				AssertionErrors.assertTrue("Not a Publisher: " + data, data instanceof Publisher);

				List<ResponseError> errors = response.getErrors();
				AssertionErrors.assertTrue("Subscription errors: " + errors, CollectionUtils.isEmpty(errors));

				return Flux.from((Publisher<ExecutionResult>) data).map((executionResult) ->
						new DefaultExecutionGraphQlResponse(response.getExecutionInput(), executionResult));
			}
			catch (AssertionError ex) {
				throw new AssertionError(ex.getMessage() + "\nRequest: " + request, ex);
			}
		});
	}

	private ExecutionGraphQlRequest toExecutionRequest(GraphQlRequest request) {
		return new DefaultExecutionGraphQlRequest(
				request.getDocument(), request.getOperationName(), request.getVariables(), request.getExtensions(),
				idGenerator.generateId().toString(), null);
	}

	/**
	 * Subclasses must implement this to execute requests.
	 */
	protected abstract Mono<ExecutionGraphQlResponse> executeInternal(ExecutionGraphQlRequest request);

}

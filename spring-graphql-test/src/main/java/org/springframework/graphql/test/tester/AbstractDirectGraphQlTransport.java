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
 * @since 1.0.0
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
		return executeInternal(toExecutionRequest(request)).flatMapMany(response -> {
			try {
				Object data = response.getData();
				AssertionErrors.assertTrue("Not a Publisher: " + data, data instanceof Publisher);

				List<ResponseError> errors = response.getErrors();
				AssertionErrors.assertTrue("Subscription errors: " + errors, CollectionUtils.isEmpty(errors));

				return Flux.from((Publisher<ExecutionResult>) data).map(executionResult ->
						new DefaultExecutionGraphQlResponse(response.getExecutionInput(), executionResult));
			}
			catch (AssertionError ex) {
				throw new AssertionError(ex.getMessage() + "\nRequest: " + request, ex);
			}
		});
	}

    @Override
    public Mono<GraphQlResponse> executeFileUpload(GraphQlRequest request) {
        throw new UnsupportedOperationException("File upload is not supported");
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

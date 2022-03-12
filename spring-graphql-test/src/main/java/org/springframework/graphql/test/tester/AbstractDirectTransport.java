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
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.RequestOutput;
import org.springframework.graphql.client.GraphQlTransport;
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
abstract class AbstractDirectTransport implements GraphQlTransport {

	protected static final IdGenerator idGenerator = new AlternativeJdkIdGenerator();


	@Override
	public Mono<ExecutionResult> execute(GraphQlRequest request) {
		return executeInternal(request).cast(ExecutionResult.class);
	}

	@SuppressWarnings({"ConstantConditions", "unchecked"})
	@Override
	public Flux<ExecutionResult> executeSubscription(GraphQlRequest request) {
		return executeInternal(request).flatMapMany(result -> {
			try {
				Object data = result.getData();
				AssertionErrors.assertTrue("Not a Publisher: " + data, data instanceof Publisher);

				List<GraphQLError> errors = result.getErrors();
				AssertionErrors.assertTrue("Subscription errors: " + errors, CollectionUtils.isEmpty(errors));

				return Flux.from((Publisher<ExecutionResult>) data);
			}
			catch (AssertionError ex) {
				throw new AssertionError(ex.getMessage() + "\nRequest: " + request, ex);
			}
		});
	}

	/**
	 * Subclasses must implement this to execute requests.
	 */
	protected abstract Mono<? extends RequestOutput> executeInternal(GraphQlRequest request);

}

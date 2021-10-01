/*
 * Copyright 2002-2021 the original author or authors.
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

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.graphql.RequestInput;
import org.springframework.lang.Nullable;
import org.springframework.test.util.AssertionErrors;
import org.springframework.util.CollectionUtils;

/**
 * Base class for a {@link RequestStrategy} that performs GraphQL requests
 * directly against a GraphQL Java server, i.e. without a client.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class DirectRequestStrategySupport extends RequestStrategySupport {


	protected DirectRequestStrategySupport(
			@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration timeout) {

		super(errorFilter, jsonPathConfig, timeout);
	}


	protected GraphQlTester.ResponseSpec createResponseSpec(RequestInput input, ExecutionResult result) {
		return createResponseSpec(result, assertDecorator(input));
	}

	protected GraphQlTester.SubscriptionSpec createSubscriptionSpec(RequestInput input, ExecutionResult result) {
		Consumer<Runnable> assertDecorator = assertDecorator(input);

		assertDecorator.accept(() -> AssertionErrors.assertTrue(
				"Subscription did not return Publisher",
				result.getData() instanceof Publisher));

		assertDecorator.accept(() -> AssertionErrors.assertTrue(
				"Response has " + result.getErrors().size() + " unexpected error(s).",
				CollectionUtils.isEmpty(result.getErrors())));

		return () -> {
			Publisher<? extends ExecutionResult> publisher = result.getData();
			return Flux.from(publisher).map((current) -> createResponseSpec(current, assertDecorator));
		};
	}

	private Consumer<Runnable> assertDecorator(RequestInput input) {
		return (assertion) -> {
			try {
				assertion.run();
			}
			catch (AssertionError ex) {
				throw new AssertionError(ex.getMessage() + "\nRequest: " + input, ex);
			}
		};
	}

}

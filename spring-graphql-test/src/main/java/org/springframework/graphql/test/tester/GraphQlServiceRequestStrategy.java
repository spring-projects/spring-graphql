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
import java.util.function.Predicate;

import com.jayway.jsonpath.Configuration;
import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.RequestInput;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link RequestStrategy} that performs requests via {@link GraphQlService}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class GraphQlServiceRequestStrategy extends DirectRequestStrategySupport implements RequestStrategy {

	private final GraphQlService graphQlService;


	public GraphQlServiceRequestStrategy(GraphQlService service,
			@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration timeout) {

		super(errorFilter, jsonPathConfig, timeout);
		Assert.notNull(service, "GraphQlService is required.");
		this.graphQlService = service;
	}


	@Override
	public GraphQlTester.ResponseSpec execute(RequestInput input) {
		return createResponseSpec(input, executeInternal(input));
	}

	@Override
	public GraphQlTester.SubscriptionSpec executeSubscription(RequestInput input) {
		return createSubscriptionSpec(input, executeInternal(input));
	}

	private ExecutionResult executeInternal(RequestInput input) {
		ExecutionResult result = this.graphQlService.execute(input).block(getResponseTimeout());
		Assert.notNull(result, "Expected ExecutionResult");
		return result;
	}

}

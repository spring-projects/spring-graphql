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
import graphql.GraphQLError;

import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link WebRequestStrategy} that performs requests directly against a
 * {@link WebGraphQlHandler}, i.e. without a client.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class WebGraphQlHandlerRequestStrategy extends DirectRequestStrategySupport implements WebRequestStrategy {

	private final WebGraphQlHandler graphQlHandler;


	WebGraphQlHandlerRequestStrategy(WebGraphQlHandler handler,
			@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration timeout) {

		super(errorFilter, jsonPathConfig, timeout);
		this.graphQlHandler = handler;
	}


	@Override
	public WebGraphQlTester.WebResponseSpec execute(WebInput input) {
		WebOutput webOutput = executeInternal(input);
		GraphQlTester.ResponseSpec responseSpec = createResponseSpec(input, webOutput);
		return DefaultWebGraphQlTester.createResponseSpec(responseSpec, webOutput.getResponseHeaders());
	}

	@Override
	public WebGraphQlTester.WebSubscriptionSpec executeSubscription(WebInput input) {
		WebOutput webOutput = executeInternal(input);
		GraphQlTester.SubscriptionSpec subscriptionSpec = createSubscriptionSpec(input, webOutput);
		return DefaultWebGraphQlTester.createSubscriptionSpec(subscriptionSpec, webOutput.getResponseHeaders());
	}

	private WebOutput executeInternal(WebInput webInput) {
		WebOutput webOutput = this.graphQlHandler.handle(webInput).block(getResponseTimeout());
		Assert.notNull(webOutput, "Expected WebOutput");
		return webOutput;
	}

}

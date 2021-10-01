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
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.lang.Nullable;

/**
 * Base class support for {@link RequestStrategy} and
 * {@link WebRequestStrategy} implementations.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class RequestStrategySupport {

	@Nullable
	private final Predicate<GraphQLError> errorFilter;

	private final Configuration jsonPathConfig;

	private final Duration responseTimeout;


	protected RequestStrategySupport(
			@Nullable Predicate<GraphQLError> errorFilter, Configuration jsonPathConfig, Duration timeout) {

		this.errorFilter = errorFilter;
		this.jsonPathConfig = jsonPathConfig;
		this.responseTimeout = timeout;
	}


	protected Configuration getJsonPathConfig() {
		return this.jsonPathConfig;
	}

	protected Duration getResponseTimeout() {
		return this.responseTimeout;
	}

	protected GraphQlTester.ResponseSpec createResponseSpec(
			ExecutionResult result, Consumer<Runnable> assertDecorator) {

		DocumentContext context = JsonPath.parse(result.toSpecification(), this.jsonPathConfig);
		return createResponseSpec(context, assertDecorator);
	}

	protected GraphQlTester.ResponseSpec createResponseSpec(
			DocumentContext context, Consumer<Runnable> assertDecorator) {

		return DefaultGraphQlTester.createResponseSpec(context, this.errorFilter, assertDecorator);
	}

}

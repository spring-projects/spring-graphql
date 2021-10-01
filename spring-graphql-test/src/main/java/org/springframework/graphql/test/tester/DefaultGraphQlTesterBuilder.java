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

import org.springframework.graphql.GraphQlService;
import org.springframework.util.Assert;

/**
 * Default implementation of a {@link GraphQlTester.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class DefaultGraphQlTesterBuilder
		extends GraphQlTesterBuilderSupport implements GraphQlTester.Builder<DefaultGraphQlTesterBuilder> {

	private final GraphQlService service;


	DefaultGraphQlTesterBuilder(GraphQlService service) {
		Assert.notNull(service, "GraphQlService is required.");
		this.service = service;
	}


	@Override
	public DefaultGraphQlTesterBuilder errorFilter(Predicate<GraphQLError> predicate) {
		addErrorFilter(predicate);
		return this;
	}

	@Override
	public DefaultGraphQlTesterBuilder jsonPathConfig(Configuration config) {
		setJsonPathConfig(config);
		return this;
	}

	@Override
	public DefaultGraphQlTesterBuilder responseTimeout(Duration timeout) {
		setResponseTimeout(timeout);
		return this;
	}

	@Override
	public GraphQlTester build() {
		RequestStrategy strategy = new GraphQlServiceRequestStrategy(
				this.service, getErrorFilter(), initJsonPathConfig(), initResponseTimeout());

		return new DefaultGraphQlTester(strategy);
	}

}

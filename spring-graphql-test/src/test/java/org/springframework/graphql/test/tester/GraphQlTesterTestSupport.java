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

import org.springframework.graphql.execution.MockExecutionGraphQlService;


/**
 * Base class for {@link GraphQlTester} tests.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlTesterTestSupport {

	private final MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();

	private final ExecutionGraphQlServiceTester.Builder<?> graphQlTesterBuilder =
			ExecutionGraphQlServiceTester.builder(this.graphQlService);

	private final GraphQlTester graphQlTester = this.graphQlTesterBuilder.build();


	public MockExecutionGraphQlService getGraphQlService() {
		return this.graphQlService;
	}

	protected String getActualRequestDocument() {
		return this.graphQlService.getGraphQlRequest().getDocument();
	}

	protected GraphQlTester graphQlTester() {
		return this.graphQlTester;
	}

	public ExecutionGraphQlServiceTester.Builder<?> graphQlTesterBuilder() {
		return this.graphQlTesterBuilder;
	}

}

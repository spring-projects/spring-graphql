/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

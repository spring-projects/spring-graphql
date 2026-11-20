/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.graphqlservicetester;

import graphql.execution.ExecutionId;

import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;

public class ServiceSetup {

	void serviceSetup() {
		// tag::serviceSetup[]
		ExecutionGraphQlService service = /**/ null;
		ExecutionGraphQlServiceTester tester = ExecutionGraphQlServiceTester.create(service);
		// end::serviceSetup[]
	}

	void customServiceSetup() {
		// tag::customServiceSetup[]
		ExecutionGraphQlService service = /**/ null;
		ExecutionId executionId = ExecutionId.generate();
		ExecutionGraphQlServiceTester tester = ExecutionGraphQlServiceTester.builder(service)
				.configureExecutionInput((executionInput, builder) -> builder.executionId(executionId).build())
				.build();
		// end::customServiceSetup[]
	}
}

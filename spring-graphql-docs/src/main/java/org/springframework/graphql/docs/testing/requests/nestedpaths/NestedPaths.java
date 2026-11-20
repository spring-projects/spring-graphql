/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.requests.nestedpaths;

import org.springframework.graphql.test.tester.GraphQlTester;

public class NestedPaths {

	void nestedPaths() {
		GraphQlTester graphQlTester = null;
		String document = "";
		// tag::nestedPaths[]
		graphQlTester.document(document)
				.execute()
				.path("project", (project) -> project // <1>
						.path("name").entity(String.class).isEqualTo("spring-framework")
						.path("releases[*].version").entityList(String.class).hasSizeGreaterThan(1));
		// end::nestedPaths[]
	}
}

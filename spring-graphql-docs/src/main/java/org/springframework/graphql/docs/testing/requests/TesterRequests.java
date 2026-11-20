/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.requests;

import org.springframework.graphql.test.tester.GraphQlTester;

public class TesterRequests {

	void inlineDocument() {
		GraphQlTester graphQlTester = null;
		// tag::inlineDocument[]
		String document =
				"""
				{
					project(slug:"spring-framework") {
						releases {
						version
						}
					}
				}
				""";

		graphQlTester.document(document)
				.execute()
				.path("project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(1);
		// end::inlineDocument[]
	}

	void documentName() {
		GraphQlTester graphQlTester = null;
		// tag::documentName[]
		graphQlTester.documentName("projectReleases") // <1>
				.variable("slug", "spring-framework") // <2>
				.execute()
				.path("projectReleases.project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(1);
		// end::documentName[]
	}

	void fragment() {
		GraphQlTester graphQlTester = null;
		// tag::fragment[]
		graphQlTester.documentName("projectReleases") // <1>
				.fragmentName("releases") // <2>
				.execute()
				.path("frameworkReleases.project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(1);
		// end::fragment[]
	}

	record Project() {

	}
}

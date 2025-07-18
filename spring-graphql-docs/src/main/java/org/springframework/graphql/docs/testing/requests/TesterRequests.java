/*
 * Copyright 2020-present the original author or authors.
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

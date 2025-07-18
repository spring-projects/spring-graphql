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

package org.springframework.graphql.docs.testing.errors;

import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.WebGraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;

public class TestErrors {

	void verifyErrors() {
		GraphQlTester graphQlTester = null;
		String query = "";
		// tag::verifyErrors[]
		graphQlTester.document(query)
				.execute()
				.errors()
				.filter((error) -> error.getMessage().equals("ignored error"))
				.verify()
				.path("project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(1);
		// end::verifyErrors[]
	}

	void setupErrorFilter() {
		WebGraphQlHandler handler = null;
		// tag::setupErrorFilter[]
		WebGraphQlTester graphQlTester = WebGraphQlTester.builder(handler)
				.errorFilter((error) -> error.getMessage().equals("ignored error"))
				.build();
		// end::setupErrorFilter[]
	}

	void expectedErrors() {
		GraphQlTester graphQlTester = null;
		String query = "";
		// tag::expectedErrors[]
		graphQlTester.document(query)
				.execute()
				.errors()
				.expect((error) -> error.getMessage().equals("expected error"))
				.verify()
				.path("project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(1);
		// end::expectedErrors[]
	}

	void satisfyErrors() {
		GraphQlTester graphQlTester = null;
		String document = "";
		// tag::satisfyErrors[]
		graphQlTester.document(document)
				.execute()
				.errors()
				.satisfy((errors) ->
						assertThat(errors)
								.anyMatch((error) -> error.getMessage().contains("ignored error"))
				);
		// end::satisfyErrors[]
	}
}

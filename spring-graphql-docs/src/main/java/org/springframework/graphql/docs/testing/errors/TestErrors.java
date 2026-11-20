/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

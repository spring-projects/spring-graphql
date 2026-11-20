/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.webgraphqltester;

import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.test.tester.WebGraphQlTester;

public class WebSetup {

	void webSetup() {
		// tag::webSetup[]
		WebGraphQlHandler handler = /**/ null;
		WebGraphQlTester tester = WebGraphQlTester.create(handler);
		// end::webSetup[]
	}

	void customWebSetup() {
		// tag::customWebSetup[]
		WebGraphQlHandler handler = /**/ null;
		WebGraphQlTester tester = WebGraphQlTester.builder(handler)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();
		// end::customWebSetup[]
	}
}

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.httpgraphqltester;

import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class HttpSetup {

	void webTestClient() {
		// tag::webTestClient[]
		AnnotationConfigWebApplicationContext context = /**/ new AnnotationConfigWebApplicationContext();

		WebTestClient client =
				WebTestClient.bindToApplicationContext(context)
						.configureClient()
						.baseUrl("/graphql")
						.build();

		HttpGraphQlTester tester = HttpGraphQlTester.create(client);
		// end::webTestClient[]
	}

	void mockMvc() {
		// tag::mockMvc[]
		AnnotationConfigWebApplicationContext context = /**/ new AnnotationConfigWebApplicationContext();

		WebTestClient client =
				MockMvcWebTestClient.bindToApplicationContext(context)
						.configureClient()
						.baseUrl("/graphql")
						.build();

		HttpGraphQlTester tester = HttpGraphQlTester.create(client);
		// end::mockMvc[]
	}

	void liveServer() {
		// tag::liveServer[]
		WebTestClient client =
				WebTestClient.bindToServer()
						.baseUrl("http://localhost:8080/graphql")
						.build();

		HttpGraphQlTester tester = HttpGraphQlTester.create(client);
		// end::liveServer[]
	}

	void executeRequests() {
		// tag::executeRequests[]
		WebTestClient.Builder clientBuilder =
				WebTestClient.bindToServer()
						.baseUrl("http://localhost:8080/graphql");

		HttpGraphQlTester tester = HttpGraphQlTester.builder(clientBuilder)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();

		// Use tester...

		HttpGraphQlTester anotherTester = tester.mutate()
				.headers((headers) -> headers.setBasicAuth("peter", "..."))
				.build();

		// Use anotherTester...
		// end::executeRequests[]
	}

	static class Configuration {

	}
}

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

	void oidcAuthentication() {
		// tag::oidcAuthentication[]
		// Note: This requires spring-security-test dependency
		WebTestClient client = WebTestClient.bindToServer()
				.baseUrl("http://localhost:8080/graphql")
				.build();

		// The following code demonstrates how to use mockOidcLogin() with HttpGraphQlTester
		// You need to add spring-security-test, spring-security-oauth2-client, and spring-security-oauth2-jose dependencies
		/*
		import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;

		HttpGraphQlTester tester = HttpGraphQlTester.create(
			client.mutateWith(
				SecurityMockServerConfigurers.mockOidcLogin()
					.oidcUser(yourOidcUser())
			)
		);
		*/

		// For documentation purposes only, this is a simplified version
		WebTestClient mutatedClient = client; // In real code, this would be client.mutateWith(mockOidcLogin().oidcUser(yourOidcUser()))
		HttpGraphQlTester tester = HttpGraphQlTester.create(mutatedClient);
		// end::oidcAuthentication[]
	}

	static class Configuration {

	}
}

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.httpgraphqlclient;

import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

public class ClientUsage {

	void create() {
		// tag::create[]
		WebClient webClient = WebClient.create("https://spring.io/graphql");
		HttpGraphQlClient graphQlClient = HttpGraphQlClient.create(webClient);
		// end::create[]
	}

	void mutateClient() {
		// tag::mutate[]
		WebClient webClient = WebClient.create("https://spring.io/graphql");

		HttpGraphQlClient graphQlClient = HttpGraphQlClient.builder(webClient)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();

		// Perform requests with graphQlClient...

		HttpGraphQlClient anotherGraphQlClient = graphQlClient.mutate()
				.headers((headers) -> headers.setBasicAuth("peter", "..."))
				.build();

		// Perform requests with anotherGraphQlClient...
		// end::mutate[]
	}
}

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.httpsyncgraphqlclient;

import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;

public class SyncClientUsage {

	void createClient() {
		// tag::create[]
		RestClient restClient = RestClient.create("https://spring.io/graphql");
		HttpSyncGraphQlClient graphQlClient = HttpSyncGraphQlClient.create(restClient);
		// end::create[]
	}

	void mutateClient() {
		// tag::mutate[]
		RestClient restClient = RestClient.create("https://spring.io/graphql");
		HttpSyncGraphQlClient graphQlClient = HttpSyncGraphQlClient.builder(restClient)
				.headers((headers) -> headers.setBasicAuth("joe", "..."))
				.build();

		// Perform requests with graphQlClient...

		HttpSyncGraphQlClient anotherGraphQlClient = graphQlClient.mutate()
				.headers((headers) -> headers.setBasicAuth("peter", "..."))
				.build();

		// Perform requests with anotherGraphQlClient...

		// end::mutate[]
	}
}

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.subscriptions.retrieve;

import reactor.core.publisher.Flux;

import org.springframework.graphql.client.GraphQlClient;

public class RetrieveSubscription {

	void subscriptionRetrieve() {
		GraphQlClient client = null;
		// tag::subscriptionRetrieve[]
		Flux<String> greetingFlux = client.document("subscription { greetings }")
				.retrieveSubscription("greeting")
				.toEntity(String.class);
		// end::subscriptionRetrieve[]
	}
}

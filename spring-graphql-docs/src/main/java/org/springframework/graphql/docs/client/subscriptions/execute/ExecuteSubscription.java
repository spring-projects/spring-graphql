/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.subscriptions.execute;

import reactor.core.publisher.Flux;

import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.GraphQlClient;

public class ExecuteSubscription {

	void subscriptionExecute() {
		GraphQlClient client = null;
		// tag::subscriptionExecute[]
		Flux<String> greetingFlux = client.document("subscription { greetings }")
				.executeSubscription()
				.map((response) -> {
					if (!response.isValid()) {
						// Request failure...
					}

					ClientResponseField field = response.field("project");
					if (field.getValue() == null) {
						if (field.getErrors().isEmpty()) {
							// Optional field set to null...
						}
						else {
							// Field failure...
						}
					}

					return field.toEntity(String.class);
				});
		// end::subscriptionExecute[]
	}
}

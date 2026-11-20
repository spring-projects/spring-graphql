/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.testing.subscriptions;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.graphql.test.tester.GraphQlTester;

public class TestSubscriptions {

	void testSubscriptions() {
		GraphQlTester tester = null;
		// tag::testSubscriptions[]
		Flux<String> greetingFlux = tester.document("subscription { greetings }")
				.executeSubscription()
				.toFlux("greetings", String.class);  // decode at JSONPath

		StepVerifier.create(greetingFlux)
				.expectNext("Hi")
				.expectNext("Bonjour")
				.expectNext("Hola")
				.verifyComplete();
		// end::testSubscriptions[]
	}
}

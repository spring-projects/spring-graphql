/*
 * Copyright 2002-2022 the original author or authors.
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
package io.spring.sample.graphql;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * GraphQL over WebSocket subscription tests.
 */
@GraphQlTest(SampleController.class)
@Import(DataRepository.class)
public class WebFluxWebSocketSampleSubscriptionTests {

	@Autowired
	private GraphQlTester graphQlTester;


	@Test
	void subscriptionWithEntityPath() {
		Flux<String> result = this.graphQlTester.document("subscription { greetings }")
				.executeSubscription()
				.toFlux("greetings", String.class);

		StepVerifier.create(result)
				.expectNext("Hi!")
				.expectNext("Bonjour!")
				.expectNext("Hola!")
				.expectNext("Ciao!")
				.expectNext("Zdravo!")
				.verifyComplete();
	}

	@Test
	void subscriptionWithResponse() {
		Flux<GraphQlTester.Response> result = this.graphQlTester.document("subscription { greetings }")
				.executeSubscription()
				.toFlux();

		StepVerifier.create(result)
				.consumeNextWith(response -> response.path("greetings").hasValue())
				.consumeNextWith(response -> response.path("greetings").matchesJson("\"Bonjour!\""))
				.consumeNextWith(response -> response.path("greetings").matchesJson("\"Hola!\""))
				.expectNextCount(2)
				.verifyComplete();
	}

}

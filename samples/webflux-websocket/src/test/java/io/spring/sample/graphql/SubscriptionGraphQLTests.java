/*
 * Copyright 2002-2021 the original author or authors.
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

import graphql.GraphQL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.WebGraphQLService;
import org.springframework.graphql.test.query.GraphQLTester;

/**
 * GraphQL subscription tests directly via {@link GraphQL}.
 */
@SpringBootTest
public class SubscriptionGraphQLTests {

	private GraphQLTester graphQLTester;


	@BeforeEach
	public void setUp(@Autowired WebGraphQLService service) {
		this.graphQLTester = GraphQLTester.create(service);
	}


	@Test
	void subscriptionWithEntityPath() {
		String query = "subscription { greetings }";

		Flux<String> result = this.graphQLTester.query(query)
				.executeSubscription()
				.toFlux("greetings", String.class);

		StepVerifier.create(result)
				.expectNext("Hi", "Bonjour", "Hola", "Ciao", "Zdravo")
				.verifyComplete();
	}

	@Test
	void subscriptionWithResponseSpec() {
		String query = "subscription { greetings }";

		Flux<GraphQLTester.ResponseSpec> result = this.graphQLTester.query(query)
				.executeSubscription()
				.toFlux();

		StepVerifier.create(result)
				.consumeNextWith(spec -> spec.path("greetings").valueExists())
				.consumeNextWith(spec -> spec.path("greetings").matchesJson("\"Bonjour\""))
				.consumeNextWith(spec -> spec.path("greetings").matchesJson("\"Hola\""))
				.expectNextCount(2)
				.verifyComplete();
	}

}

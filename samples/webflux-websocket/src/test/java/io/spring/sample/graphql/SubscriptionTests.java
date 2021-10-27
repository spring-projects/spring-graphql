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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.boot.test.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * GraphQL subscription tests directly via {@link GraphQL}.
 */
@GraphQlTest(controllers = SampleController.class,
		includeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {ContextWebFilter.class, DataRepository.class}))
public class SubscriptionTests {

	private GraphQlTester graphQlTester;

	@BeforeEach
	public void setUp(@Autowired GraphQlService service) {
		this.graphQlTester = GraphQlTester.create(requestInput ->
				service.execute(requestInput).contextWrite(context -> context.put("name", "James")));
	}

	@Test
	void subscriptionWithEntityPath() {
		Flux<String> result = this.graphQlTester.query("subscription { greetings }")
				.executeSubscription()
				.toFlux("greetings", String.class);

		StepVerifier.create(result)
				.expectNext("Hi James")
				.expectNext("Bonjour James")
				.expectNext("Hola James")
				.expectNext("Ciao James")
				.expectNext("Zdravo James")
				.verifyComplete();
	}

	@Test
	void subscriptionWithResponseSpec() {
		Flux<GraphQlTester.ResponseSpec> result = this.graphQlTester.query("subscription { greetings }")
				.executeSubscription()
				.toFlux();

		StepVerifier.create(result)
				.consumeNextWith(spec -> spec.path("greetings").valueExists())
				.consumeNextWith(spec -> spec.path("greetings").matchesJson("\"Bonjour James\""))
				.consumeNextWith(spec -> spec.path("greetings").matchesJson("\"Hola James\""))
				.expectNextCount(2)
				.verifyComplete();
	}

}

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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * GraphQL over WebSocket single response tests.
 */
@GraphQlTest(SampleController.class)
@Import(DataRepository.class)
public class WebFluxWebSocketSampleTests {

	@Autowired
	private GraphQlTester graphQlTester;


	@Test
	void greetingMono() {
		this.graphQlTester.document("{greetingMono}")
				.execute()
				.path("greetingMono")
				.entity(String.class)
				.isEqualTo("Hello!");
	}

	@Test
	void greetingsFlux() {
		this.graphQlTester.document("{greetingsFlux}")
				.execute()
				.path("greetingsFlux")
				.entityList(String.class)
				.containsExactly("Hi!", "Bonjour!", "Hola!", "Ciao!", "Zdravo!");
	}

}

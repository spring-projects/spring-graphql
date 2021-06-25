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

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;

// @formatter:off

@SpringBootTest()
class SampleApplicationTests {

	@Autowired
	private ReactiveWebApplicationContext context;
	private static final String BASE_URL = "https://spring.example.org/graphql";


	WebTestClient client;

	@BeforeEach
	public void setup() {
		this.client = WebTestClient
				.bindToApplicationContext(this.context)
				.apply(SecurityMockServerConfigurers.springSecurity())
				.configureClient()
				.filter(ExchangeFilterFunctions.basicAuthentication())
				.defaultHeaders(headers -> {
					headers.setContentType(MediaType.APPLICATION_JSON);
					headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				})
				.baseUrl(BASE_URL)
				.build();
	}

	@Test
	void printError() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";


		client.post().uri("")
				.bodyValue("{  \"query\": \"" + query + "\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.consumeWith(System.out::println);

	}

	@Test
	void anonoymousThenUnauthorized() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		client.post().uri("")
				.bodyValue("{  \"query\": \"" + query + "\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("errors[0].extensions.classification").isEqualTo("UNAUTHORIZED");

	}

	@Test
	void userRoleThenForbidden() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		client.post().uri("")
				.headers(h -> h.setBasicAuth("rob", "rob"))
				.bodyValue("{  \"query\": \"" + query + "\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("errors[0].extensions.classification").isEqualTo("FORBIDDEN");
	}

	@Test
	void canQueryName() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"  }" +
				"}";


		client.post().uri("")
				.bodyValue("{  \"query\": \"" + query + "\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("data.employees[0].name").isEqualTo("Andi");

	}

	@Test
	void canNotQuerySalary() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";


		client.post().uri("")
				.bodyValue("{  \"query\": \"" + query + "\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("data.employees[0].name").isEqualTo("Andi")
				.jsonPath("data.employees[0].salary").doesNotExist();

	}

	@Test
	void canQuerySalaryAsAdmin() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";


		client.post().uri("")
				.headers(h -> h.setBasicAuth("admin", "admin"))
				.bodyValue("{  \"query\": \"" + query + "\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("data.employees[0].name").isEqualTo("Andi")
				.jsonPath("data.employees[0].salary").isEqualTo("42");

	}

	@Test
	void invalidCredentials() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";


		client.post().uri("")
				.headers(h -> h.setBasicAuth("admin", "INVALID"))
				.bodyValue("{  \"query\": \"" + query + "\"}")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectBody()
				.isEmpty();

	}
}
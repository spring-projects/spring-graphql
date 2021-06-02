package io.spring.sample.graphql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;

import java.util.Collections;

@SpringBootTest()
class EmployeeServiceApplicationTest {

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
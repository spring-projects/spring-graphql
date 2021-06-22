package io.spring.sample.graphql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

// @formatter:off

@SpringBootTest
@AutoConfigureMockMvc
class SampleApplicationTests {

	private WebTestClient client;

	@BeforeEach
	public void setUp(@Autowired MockMvc mockMvc) {
		this.client = MockMvcWebTestClient.bindTo(mockMvc)
				.baseUrl("/graphql")
				.defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
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
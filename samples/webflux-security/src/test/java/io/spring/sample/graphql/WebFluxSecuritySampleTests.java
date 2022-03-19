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

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.WebGraphQlTester;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WebFluxSecuritySampleTests {

	@LocalServerPort
	private int port;

	private WebSocketGraphQlTester graphQlTester;

	@BeforeEach
	public void setUp() {
		URI url = URI.create("http://localhost:" + this.port + "/graphql");
		this.graphQlTester = WebSocketGraphQlTester.create(url, new ReactorNettyWebSocketClient());
	}

	@AfterEach
	void tearDown() {
		this.graphQlTester.stop().block(Duration.ofSeconds(5));
	}

	@Test
	void printError() {
		this.graphQlTester.documentName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(System.out::println);
	}

	@Test
	void anonymousThenUnauthorized() {
		this.graphQlTester.documentName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
				});
	}

	@Test
	void userRoleThenForbidden() {

		WebGraphQlTester authTester = this.graphQlTester.mutate()
				.headers(headers -> headers.setBasicAuth("rob", "rob"))
				.build();

		authTester.documentName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
				});
	}

	@Test
	void canQueryName() {
		this.graphQlTester.documentName("employeesNames")
				.execute()
				.path("employees[0].name").entity(String.class).isEqualTo("Andi");
	}

	@Test
	void canNotQuerySalary() {
		this.graphQlTester.documentName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
				});
	}

	@Test
	void canQuerySalaryAsAdmin() {

		WebGraphQlTester authTester = this.graphQlTester.mutate()
				.headers(headers -> headers.setBasicAuth("admin", "admin"))
				.build();

		authTester.documentName("employeesNamesAndSalaries")
				.execute()
				.path("employees[0].name").entity(String.class).isEqualTo("Andi")
				.path("employees[0].salary").entity(int.class).isEqualTo(42);
	}

	@Test
	void invalidCredentials() {
		assertThatThrownBy(() ->
				this.graphQlTester.mutate()
						.headers(headers -> headers.setBasicAuth("admin", "INVALID"))
						.build()
						.documentName("employeesNamesAndSalaries")
						.executeAndVerify())
				.hasMessage(
						"GraphQlTransport error: Invalid handshake response getStatus: 401 Unauthorized; " +
								"nested exception is io.netty.handler.codec.http.websocketx.WebSocketClientHandshakeException: " +
								"Invalid handshake response getStatus: 401 Unauthorized");
	}

}
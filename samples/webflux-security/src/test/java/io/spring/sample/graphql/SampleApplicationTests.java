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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.boot.test.tester.AutoConfigureGraphQlTester;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.WebGraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest()
@AutoConfigureWebTestClient
@AutoConfigureGraphQlTester
class SampleApplicationTests {

	@Autowired
	private WebGraphQlTester graphQlTester;

	@Test
	void printError() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.errors()
				.satisfy(System.out::println);
	}

	@Test
	void anonymousThenUnauthorized() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
				});
	}

	@Test
	void userRoleThenForbidden() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.headers(headers -> headers.setBasicAuth("rob", "rob"))
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
				});
	}

	@Test
	void canQueryName() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.path("employees[0].name").entity(String.class).isEqualTo("Andi");
	}

	@Test
	void canNotQuerySalary() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
				});
	}

	@Test
	void canQuerySalaryAsAdmin() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.headers(headers -> headers.setBasicAuth("admin", "admin"))
				.execute()
				.path("employees[0].name").entity(String.class).isEqualTo("Andi")
				.path("employees[0].salary").entity(int.class).isEqualTo(42);
	}

	@Test
	void invalidCredentials() {
		String query = "{" +
				"  employees{ " +
				"    name" +
				"    salary" +
				"  }" +
				"}";

		assertThatThrownBy(() ->
				this.graphQlTester.query(query)
						.headers(headers -> headers.setBasicAuth("admin", "INVALID"))
						.executeAndVerify())
				.hasMessage("Status expected:<200 OK> but was:<401 UNAUTHORIZED>");
	}

}
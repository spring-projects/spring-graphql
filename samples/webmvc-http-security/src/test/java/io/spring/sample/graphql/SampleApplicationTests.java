package io.spring.sample.graphql;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureWebGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.WebGraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureWebGraphQlTester
class SampleApplicationTests {

	@Autowired
	private WebGraphQlTester graphQlTester;


	@Test
	void printError() {
		this.graphQlTester.operationName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(System.out::println);
	}

	@Test
	void anonymousThenUnauthorized() {
		this.graphQlTester.operationName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
				});
	}

	@Test
	void userRoleThenForbidden() {
		this.graphQlTester.operationName("employeesNamesAndSalaries")
				.httpHeaders(headers -> headers.setBasicAuth("rob", "rob"))
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
				});
	}

	@Test
	void canQueryName() {
		this.graphQlTester.operationName("employeesNames")
				.execute()
				.path("employees[0].name").entity(String.class).isEqualTo("Andi");
	}

	@Test
	void canNotQuerySalary() {
		this.graphQlTester.operationName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
				});
	}

	@Test
	void canQuerySalaryAsAdmin() {
		this.graphQlTester.operationName("employeesNamesAndSalaries")
				.httpHeaders(headers -> headers.setBasicAuth("admin", "admin"))
				.execute()
				.path("employees[0].name").entity(String.class).isEqualTo("Andi")
				.path("employees[0].salary").entity(int.class).isEqualTo(42);
	}

	@Test
	void invalidCredentials() {
		assertThatThrownBy(() ->
				this.graphQlTester.operationName("employeesNamesAndSalaries")
						.httpHeaders(headers -> headers.setBasicAuth("admin", "INVALID"))
						.executeAndVerify())
				.hasMessage("Status expected:<200 OK> but was:<401 UNAUTHORIZED>");
	}
}
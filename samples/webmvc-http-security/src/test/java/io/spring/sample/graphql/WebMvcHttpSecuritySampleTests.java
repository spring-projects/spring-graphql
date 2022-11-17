package io.spring.sample.graphql;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.WebGraphQlTester;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureHttpGraphQlTester
class WebMvcHttpSecuritySampleTests {

	@Autowired
	private WebGraphQlTester graphQlTester;


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

		WebGraphQlTester tester = this.graphQlTester.mutate()
				.headers(headers -> headers.setBasicAuth("rob", "rob"))
				.build();

		tester.documentName("employeesNamesAndSalaries")
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
				});
	}

	@Test
	void candocumentName() {
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
	void canNotMutateUpdateSalary() {
		SalaryInput salaryInput = new SalaryInput("1", BigDecimal.valueOf(44));

		this.graphQlTester.documentName("updateSalary")
				.variable("salaryInput", salaryInput)
				.execute()
				.errors()
				.satisfy(errors -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
				});
	}

	@Test
	void canQuerySalaryAsAdmin() {

		WebGraphQlTester tester = this.graphQlTester.mutate()
				.headers(headers -> headers.setBasicAuth("admin", "admin"))
				.build();

		tester.documentName("employeesNamesAndSalaries")
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
				.hasMessage("Status expected:<200 OK> but was:<401 UNAUTHORIZED>");
	}

}

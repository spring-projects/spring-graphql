package io.spring.sample.graphql;

import java.math.BigDecimal;
import java.util.Map;

import graphql.schema.idl.RuntimeWiring;

import org.springframework.graphql.boot.RuntimeWiringBuilderCustomizer;
import org.springframework.stereotype.Component;

@Component
public class SampleWiring implements RuntimeWiringBuilderCustomizer {

	final EmployeeService employeeService;

	final SalaryService salaryService;

	public SampleWiring(EmployeeService employeeService, SalaryService salaryService) {
		this.employeeService = employeeService;
		this.salaryService = salaryService;
	}


	@Override
	public void customize(RuntimeWiring.Builder builder) {
		builder.type("Query", wiringBuilder ->
			wiringBuilder.dataFetcher("employees", env ->
				employeeService.getAllEmployees()
			)
		);
		builder.type("Employee", wiringBuilder ->
			wiringBuilder.dataFetcher("salary",  env -> {
				Employee employee = env.getSource();
				return salaryService.getSalaryForEmployee(employee);
			})
		);
		builder.type("Mutation", wiringBuilder ->
			wiringBuilder.dataFetcher("updateSalary", env -> {
				Map<String, String> input = env.getArgument("input");
				String employeeId = input.get("employeeId");
				BigDecimal newSalary = new BigDecimal(input.get("salary"));
				salaryService.updateSalary(employeeId, newSalary);
				return null;
			})
		);
	}

}

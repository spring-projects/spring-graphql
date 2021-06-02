package io.spring.sample.graphql;

import java.math.BigDecimal;
import java.util.Map;

import graphql.schema.DataFetcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataFetchers {

	@Autowired
	EmployeeService employeeService;

	public DataFetcher employeeDataFetcher = env -> {
		return employeeService.getAllEmployees();
	};

	@Autowired
	SalaryService salaryService;

	public DataFetcher salaryDataFetcher = env -> {
		Employee employee = env.getSource();
		return salaryService.getSalaryForEmployee(employee);
	};

	public DataFetcher updateSalaryFetcher = env -> {
		Map<String, String> input = env.getArgument("input");
		String employeeId = input.get("employeeId");
		BigDecimal newSalary = new BigDecimal(input.get("salary"));
		salaryService.updateSalary(employeeId, newSalary);
		return null;
	};

}

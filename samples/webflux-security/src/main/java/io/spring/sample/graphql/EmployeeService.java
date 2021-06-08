package io.spring.sample.graphql;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class EmployeeService {

	public List<Employee> getAllEmployees() {
		return Arrays.asList(new Employee("1", "Andi"));
	}

}

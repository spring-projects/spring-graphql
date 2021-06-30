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
		builder.type("Query", wiring ->
			wiring.dataFetcher("employees", env -> this.employeeService.getAllEmployees())
		);
		builder.type("Employee", wiring ->
			wiring.dataFetcher("salary",  env -> this.salaryService.getSalaryForEmployee(env.getSource()))
		);
		builder.type("Mutation", wiring ->
			wiring.dataFetcher("updateSalary", env -> {
				Map<String, String> input = env.getArgument("input");
				String employeeId = input.get("employeeId");
				BigDecimal newSalary = new BigDecimal(input.get("salary"));
				this.salaryService.updateSalary(employeeId, newSalary);
				return null;
			})
		);
	}

}

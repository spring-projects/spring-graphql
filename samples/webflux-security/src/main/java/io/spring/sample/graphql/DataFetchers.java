package io.spring.sample.graphql;

import graphql.schema.DataFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class DataFetchers {

    @Autowired
    EmployeeService employeeService;

    @Autowired
    SalaryService salaryService;

    public DataFetcher employeeDataFetcher = env -> {
        return employeeService.getAllEmployees();
    };

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

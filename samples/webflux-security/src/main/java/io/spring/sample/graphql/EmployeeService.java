package io.spring.sample.graphql;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class EmployeeService {

    public List<Employee> getAllEmployees() {
        return Arrays.asList(new Employee("1", "Andi"));
    }

}

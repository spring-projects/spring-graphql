package io.spring.sample.graphql;

import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Component
public class SalaryService {


    @PreAuthorize("hasRole('MANAGER')")
    public Mono<BigDecimal> getSalaryForEmployee(Employee employee) {
        return Mono.just(new BigDecimal("42"));
    }

    @Secured({"ROLE_HR"})
    public void updateSalary(String employeeId, BigDecimal newSalary) {

    }
}

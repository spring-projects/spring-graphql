package org.springframework.boot.graphql;

import java.util.Collections;
import java.util.Map;

/**
 * Spring data repo meta data
 */
public class RepositoryProperties {
    /**
     * spring data repo bean name
     */
    private String beanName;
    /**
     * spring data repo method name like findAll, findById
     */
    private String method;
    /**
     * argument name : argument type class map
     */
    private Map<String, String> arguments= Collections.emptyMap();

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }
}

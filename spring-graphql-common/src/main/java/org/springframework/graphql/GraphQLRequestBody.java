package org.springframework.graphql;

import java.util.Map;

public class GraphQLRequestBody {
    private final String query;
    private final String operationName;
    private final Map<String, Object> variables;

    public GraphQLRequestBody(String query, String operationName, Map<String, Object> variables) {
        this.query = query;
        this.operationName = operationName;
        this.variables = variables;
    }

    public String getQuery() {
        return query;
    }

    public String getOperationName() {
        return operationName;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }
}

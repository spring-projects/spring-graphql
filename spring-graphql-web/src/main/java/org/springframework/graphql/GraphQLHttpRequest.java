package org.springframework.graphql;

import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

import java.util.Map;

public class GraphQLHttpRequest {
    private final String query;
    private final String operationName;
    private final Map<String, Object> variables;
    private final HttpHeaders httpHeaders;
    private final MultiValueMap<String, String> requestParams;

    public GraphQLHttpRequest(String query,
                              String operationName,
                              Map<String, Object> variables,
                              HttpHeaders httpHeaders,
                              MultiValueMap<String, String> requestParams) {
        this.query = query;
        this.operationName = operationName;
        this.variables = variables;
        this.httpHeaders = httpHeaders;
        this.requestParams = requestParams;
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

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    public MultiValueMap<String, String> getRequestParams() {
        return requestParams;
    }
}

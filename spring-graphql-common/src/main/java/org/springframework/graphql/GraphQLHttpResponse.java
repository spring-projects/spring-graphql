package org.springframework.graphql;

import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

public class GraphQLHttpResponse {

    private final Object data;
    private final List<Map<String, Object>> errors;
    private final Map<String, Object> extensions;
    private final HttpHeaders httpHeaders;

    public GraphQLHttpResponse(Object data,
                               List<Map<String, Object>> errors,
                               Map<String, Object> extensions,
                               HttpHeaders httpHeaders) {
        this.data = data;
        this.errors = errors;
        this.extensions = extensions;
        this.httpHeaders = httpHeaders;
    }

    public Object getData() {
        return data;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }
}

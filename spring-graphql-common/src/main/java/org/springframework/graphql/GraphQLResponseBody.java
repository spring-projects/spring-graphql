package org.springframework.graphql;

import java.util.List;
import java.util.Map;

public class GraphQLResponseBody {

    private final Object data;
    private final List<Map<String, Object>> errors;
    private final Map<String, Object> extensions;

    public GraphQLResponseBody(Object data, List<Map<String, Object>> errors, Map<String, Object> extensions) {
        this.data = data;
        this.errors = errors;
        this.extensions = extensions;
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
}

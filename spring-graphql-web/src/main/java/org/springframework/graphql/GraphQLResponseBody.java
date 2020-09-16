package org.springframework.graphql;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GraphQLResponseBody<T> {

	private T data;

	private List<Map<String, Object>> errors = Collections.emptyList();

	public GraphQLResponseBody() {
	}

	public GraphQLResponseBody(T data) {
		this.data = data;
		this.errors = errors;
	}

	public T getData() {
		return this.data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public List<Map<String, Object>> getErrors() {
		return this.errors;
	}

	public void setErrors(List<Map<String, Object>> errors) {
		this.errors = errors;
	}
}

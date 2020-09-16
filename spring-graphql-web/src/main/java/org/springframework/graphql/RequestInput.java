package org.springframework.graphql;

import java.util.Collections;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * @author Andreas Marek
 * @author Brian Clozel
 */
class RequestInput {

	private String query;

	private String operationName;

	private Map<String, Object> variables = Collections.emptyMap();

	public RequestInput(String query, String operationName, Map<String, Object> variables) {
		this.query = query;
		this.operationName = operationName;
		this.variables = variables;
	}

	public RequestInput() {
	}

	@Nullable
	public String getQuery() {
		return this.query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getOperationName() {
		return this.operationName;
	}

	public void setOperationName(String operationName) {
		this.operationName = operationName;
	}

	public Map<String, Object> getVariables() {
		return this.variables;
	}

	public void setVariables(Map<String, Object> variables) {
		this.variables = variables;
	}
}

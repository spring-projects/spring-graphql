/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Represents a GraphQL request with the inputs to pass to a GraphQL service
 * including a {@link #getDocument() document}, {@link #getOperationName()
 * operationName}, and {@link #getVariables() variables}.
 *
 * <p>The request can be turned to a Map via {@link #toMap()} and to be
 * submitted as JSON over HTTP or WebSocket.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlRequest {

	private final String document;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;


	/**
	 * Create a request.
	 * @param document textual representation of the operation(s)
	 */
	public GraphQlRequest(String document) {
		this(document, null, null);
	}

	/**
	 * Create a request with a complete set of inputs.
	 * @param document textual representation of the operation(s)
	 * @param operationName optionally, the name of the operation to execute
	 * @param variables variables by which the operation is parameterized
	 */
	public GraphQlRequest(String document, @Nullable String operationName, @Nullable Map<String, Object> variables) {
		Assert.notNull(document, "'document' is required");
		this.document = document;
		this.operationName = operationName;
		this.variables = ((variables != null) ? variables : Collections.emptyMap());
	}


	/**
	 * Return the GraphQL document which is the textual representation of an
	 * operation (or operations) to perform, including any selection sets and
	 * fragments.
	 */
	public String getDocument() {
		return this.document;
	}

	/**
	 * Return the name of the operation in the {@link #getDocument() document}
	 * to execute, if the document contains multiple operations.
	 */
	@Nullable
	public String getOperationName() {
		return this.operationName;
	}

	/**
	 * Return values for variable defined by the operation.
	 */
	public Map<String, Object> getVariables() {
		return this.variables;
	}

	/**
	 * Convert the request to a {@link Map} as defined in
	 * <a href="https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md">GraphQL over HTTP</a> and
	 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL over WebSocket</a>:
	 * <table>
	 * <tr><th>Key</th><th>Value</th></tr>
	 * <tr><td>query</td><td>{@link #getDocument() document}</td></tr>
	 * <tr><td>operationName</td><td>{@link #getOperationName() operationName}</td></tr>
	 * <tr><td>variables</td><td>{@link #getVariables() variables}</td></tr>
	 * </table>
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>(3);
		map.put("query", getDocument());
		if (getOperationName() != null) {
			map.put("operationName", getOperationName());
		}
		if (!CollectionUtils.isEmpty(getVariables())) {
			map.put("variables", new LinkedHashMap<>(getVariables()));
		}
		return map;
	}

	@Override
	public String toString() {
		return "document='" + getDocument() + "'" +
				((getOperationName() != null) ? ", operationName='" + getOperationName() + "'" : "") +
				(!CollectionUtils.isEmpty(getVariables()) ? ", variables=" + getVariables() : "");
	}

}

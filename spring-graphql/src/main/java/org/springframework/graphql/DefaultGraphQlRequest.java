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
import org.springframework.util.ObjectUtils;

/**
 * Default implementation of {@link GraphQlRequest}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultGraphQlRequest implements GraphQlRequest {

	private final String document;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;


	/**
	 * Create a request.
	 * @param document textual representation of the operation(s)
	 */
	public DefaultGraphQlRequest(String document) {
		this(document, null, null);
	}

	/**
	 * Create a request with a complete set of inputs.
	 * @param document textual representation of the operation(s)
	 * @param operationName optionally, the name of the operation to execute
	 * @param variables variables by which the operation is parameterized
	 */
	public DefaultGraphQlRequest(
			String document, @Nullable String operationName, @Nullable Map<String, Object> variables) {

		Assert.notNull(document, "'document' is required");
		this.document = document;
		this.operationName = operationName;
		this.variables = (variables != null ? variables : Collections.emptyMap());
	}


	@Override
	public String getDocument() {
		return this.document;
	}

	@Override
	@Nullable
	public String getOperationName() {
		return this.operationName;
	}

	@Override
	public Map<String, Object> getVariables() {
		return this.variables;
	}

	@Override
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
	public boolean equals(Object o) {
		if (! (o instanceof DefaultGraphQlRequest)) {
			return false;
		}
		DefaultGraphQlRequest other = (DefaultGraphQlRequest) o;
		return (getDocument().equals(other.getDocument()) &&
				ObjectUtils.nullSafeEquals(getOperationName(), other.getOperationName()) &&
				ObjectUtils.nullSafeEquals(getVariables(), other.getVariables()));
	}

	@Override
	public int hashCode() {
		return this.document.hashCode() +
				31 * ObjectUtils.nullSafeHashCode(this.operationName) +
				31 * this.variables.hashCode();
	}

	@Override
	public String toString() {
		return "document='" + getDocument() + "'" +
				((getOperationName() != null) ? ", operationName='" + getOperationName() + "'" : "") +
				(!CollectionUtils.isEmpty(getVariables()) ? ", variables=" + getVariables() : "");
	}

}

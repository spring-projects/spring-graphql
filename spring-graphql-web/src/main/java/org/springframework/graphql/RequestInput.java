/*
 * Copyright 2020-2020 the original author or authors.
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
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;

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

	public void validate() {
		if (!StringUtils.hasText(getQuery())) {
			throw new ServerWebInputException("Missing query");
		}
	}
}

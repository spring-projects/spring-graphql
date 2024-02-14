/*
 * Copyright 2002-2024 the original author or authors.
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
package org.springframework.graphql.server.support;

import java.util.Map;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerWebInputException;


/**
 * {@link GraphQlRequest} for deserialization from a request.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.5
 */
public class SerializableGraphQlRequest implements GraphQlRequest {

	@Nullable
	private String query;

	@Nullable
	private String operationName;

	@Nullable
	private Map<String, Object> variables;

	@Nullable
	private Map<String, Object> extensions;


	public void setQuery(String query) {
		this.query = query;
	}

	@Nullable
	public String getQuery() {
		return this.query;
	}

	public void setOperationName(@Nullable String operationName) {
		this.operationName = operationName;
	}

	@Nullable
	@Override
	public String getOperationName() {
		return this.operationName;
	}

	public void setVariables(Map<String, Object> variables) {
		this.variables = variables;
	}

	@Nullable
	@Override
	public Map<String, Object> getVariables() {
		return this.variables;
	}

	public void setExtensions(Map<String, Object> extensions) {
		this.extensions = extensions;
	}

	@Nullable
	@Override
	public Map<String, Object> getExtensions() {
		return this.extensions;
	}

	@Override
	public String getDocument() {
		if (this.query == null) {
			throw new ServerWebInputException("No 'query'");
		}
		return this.query;
	}

	@Override
	public Map<String, Object> toMap() {
		throw new UnsupportedOperationException();
	}

}

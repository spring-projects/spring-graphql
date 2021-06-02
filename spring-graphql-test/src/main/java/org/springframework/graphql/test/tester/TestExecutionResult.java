/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.graphql.test.tester;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;

/**
 * {@link GraphQLError} with setters for deserialization.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class TestExecutionResult implements ExecutionResult {

	private Object data;

	private List<GraphQLError> errors = Collections.emptyList();

	private Map<Object, Object> extensions = Collections.emptyMap();

	public void setData(Object data) {
		this.data = data;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getData() {
		return (T) this.data;
	}

	public void setErrors(List<TestGraphQlError> errors) {
		this.errors = new ArrayList<>(errors);
	}

	@Override
	public List<GraphQLError> getErrors() {
		return this.errors;
	}

	@Override
	public boolean isDataPresent() {
		return getData() != null;
	}

	public void setExtensions(Map<Object, Object> extensions) {
		this.extensions = new LinkedHashMap<>(extensions);
	}

	@Override
	public Map<Object, Object> getExtensions() {
		return this.extensions;
	}

	@Override
	public Map<String, Object> toSpecification() {
		ExecutionResultImpl.Builder builder = ExecutionResultImpl.newExecutionResult().addErrors(this.errors)
				.extensions(this.extensions);

		if (isDataPresent()) {
			builder.data(this.data);
		}

		return builder.build().toSpecification();
	}

}

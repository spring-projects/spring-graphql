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

package org.springframework.graphql.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;

import org.springframework.lang.Nullable;

/**
 * Implementation of {@link ExecutionResult} backed by a {@link Map}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class MapExecutionResult implements ExecutionResult {

	private final Map<String, Object> map;

	private final List<GraphQLError> errors;


	MapExecutionResult(@Nullable Map<String, Object> map) {
		this.map = (map != null ? map : Collections.emptyMap());
		this.errors = MapGraphQlError.fromResultMap(map);
	}


	@Override
	public List<GraphQLError> getErrors() {
		return this.errors;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getData() {
		return (T) this.map.get("data");
	}

	@Override
	public boolean isDataPresent() {
		return (this.map.get("data") != null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<Object, Object> getExtensions() {
		return (Map<Object, Object>) this.map.get("extensions");
	}

	@Override
	public Map<String, Object> toSpecification() {
		return ExecutionResultImpl.newExecutionResult().from(this).build().toSpecification();
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof MapExecutionResult && this.map.equals(((MapExecutionResult) other).map));
	}

	@Override
	public int hashCode() {
		return this.map.hashCode();
	}

	@Override
	public String toString() {
		return this.map.toString();
	}


	/**
	 * Static factory method to create an instance of this class.
	 */
	public static ExecutionResult forData(@Nullable Map<String, Object> map) {
		return new MapExecutionResult(Collections.singletonMap("data", map));
	}

}

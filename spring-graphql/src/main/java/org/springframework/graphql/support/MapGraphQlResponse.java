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

package org.springframework.graphql.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.GraphQLError;

import org.springframework.graphql.GraphQlResponse;
import org.springframework.util.Assert;

/**
 * {@link GraphQlResponse} for client use that wraps the GraphQL response map.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public final class MapGraphQlResponse implements GraphQlResponse {

	private final Map<String, Object> resultMap;

	private final List<GraphQLError> errors;


	@SuppressWarnings("unchecked")
	private MapGraphQlResponse(Map<String, Object> resultMap) {
		Assert.notNull(resultMap, "'resultMap' is required");
		this.resultMap = resultMap;
		this.errors = MapGraphQlError.from((List<Map<String, Object>>) resultMap.get("errors"));
	}


	@Override
	public boolean isValid() {
		return (this.resultMap.containsKey("data") && this.resultMap.get("data") != null);
	}

	@Override
	public List<GraphQLError> getErrors() {
		return this.errors;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getData() {
		return (T) this.resultMap.get("data");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<Object, Object> getExtensions() {
		return (Map<Object, Object>) this.resultMap.getOrDefault("extensions", Collections.emptyMap());
	}

	@Override
	public Map<String, Object> toMap() {
		return this.resultMap;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof MapGraphQlResponse &&
				this.resultMap.equals(((MapGraphQlResponse) other).resultMap));
	}

	@Override
	public int hashCode() {
		return this.resultMap.hashCode();
	}

	@Override
	public String toString() {
		return this.resultMap.toString();
	}


	/**
	 * Create an instance from an {@code ExecutionResult} serialized to map via
	 * {@link ExecutionResult#toSpecification()}.
	 */
	public static GraphQlResponse forResponse(Map<String, Object> map) {
		return new MapGraphQlResponse(map);
	}

	/**
	 * Create an {@code ExecutionResult} with a "data" key that returns the
	 * given map.
	 */
	public static GraphQlResponse forDataOnly(Map<String, Object> map) {
		return new MapGraphQlResponse(Collections.singletonMap("data", map));
	}

	/**
	 * Create an {@code ExecutionResult} with an "errors" key that returns the
	 * given serialized errors.
	 */
	public static GraphQlResponse forErrorsOnly(List<Map<String, Object>> errors) {
		return new MapGraphQlResponse(Collections.singletonMap("errors", errors));
	}

}

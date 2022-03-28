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
import java.util.stream.Collectors;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.support.AbstractGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * {@link GraphQlResponse} that wraps a deserialized the GraphQL response map.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class ResponseMapGraphQlResponse extends AbstractGraphQlResponse {

	private final Map<String, Object> responseMap;

	private final List<ResponseError> errors;


	ResponseMapGraphQlResponse(Map<String, Object> responseMap) {
		Assert.notNull(responseMap, "'responseMap' is required");
		this.responseMap = responseMap;
		this.errors = wrapErrors(responseMap);
	}

	protected ResponseMapGraphQlResponse(GraphQlResponse response) {
		Assert.notNull(response, "'GraphQlResponse' is required");
		this.responseMap = response.toMap();
		this.errors =  response.getErrors();
	}

	@SuppressWarnings("unchecked")
	private static List<ResponseError> wrapErrors(Map<String, Object> map) {
		List<Map<String, Object>> errors = (List<Map<String, Object>>) map.get("errors");
		errors = (errors != null ? errors : Collections.emptyList());
		return errors.stream().map(Error::new).collect(Collectors.toList());
	}


	@Override
	public boolean isValid() {
		return (this.responseMap.containsKey("data") && this.responseMap.get("data") != null);
	}

	@Override
	public List<ResponseError> getErrors() {
		return this.errors;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getData() {
		return (T) this.responseMap.get("data");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<Object, Object> getExtensions() {
		return (Map<Object, Object>) this.responseMap.getOrDefault("extensions", Collections.emptyMap());
	}

	@Override
	public Map<String, Object> toMap() {
		return this.responseMap;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ResponseMapGraphQlResponse &&
				this.responseMap.equals(((ResponseMapGraphQlResponse) other).responseMap));
	}

	@Override
	public int hashCode() {
		return this.responseMap.hashCode();
	}

	@Override
	public String toString() {
		return this.responseMap.toString();
	}


	/**
	 * {@link GraphQLError} that wraps a deserialized the GraphQL response map.
	 */
	private static final class Error implements ResponseError {

		private final Map<String, Object> errorMap;

		private final List<SourceLocation> locations;

		private final String path;

		Error(Map<String, Object> errorMap) {
			Assert.notNull(errorMap, "'errorMap' is required");
			this.errorMap = errorMap;
			this.locations = initLocations(errorMap);
			this.path = initPath(errorMap);
		}

		@SuppressWarnings("unchecked")
		private static List<SourceLocation> initLocations(Map<String, Object> errorMap) {
			return ((List<Map<String, Object>>) errorMap.getOrDefault("locations", Collections.emptyList())).stream()
					.map(map -> new SourceLocation((int) map.get("line"), (int) map.get("column"), (String) map.get("sourceName")))
					.collect(Collectors.toList());
		}

		@SuppressWarnings("unchecked")
		private static String initPath(Map<String, Object> errorMap) {
			List<Object> path = (List<Object>) errorMap.get("path");
			if (path == null) {
				return "";
			}
			return path.stream().reduce("",
					(s, o) -> s + (o instanceof Integer ? "[" + o + "]" : (s.isEmpty() ? o : "." + o)),
					(s, s2) -> null);
		}

		@Override
		@Nullable
		public String getMessage() {
			return (String) errorMap.get("message");
		}

		@Override
		public List<SourceLocation> getLocations() {
			return this.locations;
		}

		@Override
		public ErrorClassification getErrorType() {
			String classification = (String) getExtensions().getOrDefault("classification", "");
			try {
				return graphql.ErrorType.valueOf(classification);
			}
			catch (IllegalArgumentException ex) {
				return org.springframework.graphql.execution.ErrorType.valueOf(classification);
			}
		}

		@Override
		public String getPath() {
			return this.path;
		}

		@SuppressWarnings("unchecked")
		@Override
		public List<Object> getParsedPath() {
			return (List<Object>) this.errorMap.getOrDefault("path", Collections.emptyList());
		}

		@SuppressWarnings("unchecked")
		@Override
		public Map<String, Object> getExtensions() {
			return (Map<String, Object>) this.errorMap.getOrDefault("extensions", Collections.emptyMap());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || this.getClass() != o.getClass()) {
				return false;
			}
			ResponseError other = (ResponseError) o;
			return (ObjectUtils.nullSafeEquals(getMessage(), other.getMessage()) &&
					ObjectUtils.nullSafeEquals(getLocations(), other.getLocations()) &&
					ObjectUtils.nullSafeEquals(getParsedPath(), other.getParsedPath()) &&
					getErrorType() == other.getErrorType());
		}

		@Override
		public int hashCode() {
			int result = 1;
			result = 31 * result + ObjectUtils.nullSafeHashCode(getMessage());
			result = 31 * result + ObjectUtils.nullSafeHashCode(getLocations());
			result = 31 * result + ObjectUtils.nullSafeHashCode(getParsedPath());
			result = 31 * result + ObjectUtils.nullSafeHashCode(getErrorType());
			return result;
		}

		@Override
		public String toString() {
			return this.errorMap.toString();
		}

	}

}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.GraphQLError;

import org.springframework.graphql.GraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link GraphQlResponse} that wraps a deserialized the GraphQL response map.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class MapGraphQlResponse implements GraphQlResponse {

	/**
	 * Returned from {@link #getFieldValue(List)} to indicate a value does not exist.
	 */
	protected final static Object NO_VALUE = new Object();


	private final Map<String, Object> responseMap;

	private final List<GraphQLError> errors;


	MapGraphQlResponse(Map<String, Object> responseMap) {
		Assert.notNull(responseMap, "'responseMap' is required");
		this.responseMap = responseMap;
		this.errors = wrapErrors(responseMap);
	}

	@SuppressWarnings("unchecked")
	private static List<GraphQLError> wrapErrors(Map<String, Object> responseMap) {
		List<Map<String, Object>> rawErrors = (List<Map<String, Object>>) responseMap.get("errors");
		if (CollectionUtils.isEmpty(rawErrors)) {
			return Collections.emptyList();
		}
		List<GraphQLError> errors = new ArrayList<>(rawErrors.size());
		for (Map<String, Object> map : rawErrors) {
			errors.add(new MapGraphQlError(map));
		}
		return errors;
	}


	@Override
	public boolean isValid() {
		return (this.responseMap.containsKey("data") && this.responseMap.get("data") != null);
	}

	@Override
	public List<GraphQLError> getErrors() {
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

	/**
	 * Parse the given field path, producing an output compatible with
	 * {@link graphql.execution.ResultPath#parse(String)} but using "." instead
	 * of "/" as separators.
	 * @param path the path to parse
	 * @return the parsed path segments and offsets, possibly empty
	 * @throws IllegalArgumentException for path syntax issues
	 */
	protected static List<Object> parseFieldPath(String path) {
		if (!StringUtils.hasText(path)) {
			return Collections.emptyList();
		}

		String invalidPathMessage = "Invalid path: '" + path + "'";
		List<Object> dataPath = new ArrayList<>();

		StringBuilder sb = new StringBuilder();
		boolean readingIndex = false;

		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			switch (c) {
				case '.':
				case '[':
					Assert.isTrue(!readingIndex, invalidPathMessage);
					break;
				case ']':
					i++;
					Assert.isTrue(readingIndex, invalidPathMessage);
					Assert.isTrue(i == path.length() || path.charAt(i) == '.', invalidPathMessage);
					break;
				default:
					sb.append(c);
					if (i < path.length() - 1) {
						continue;
					}
			}
			String token = sb.toString();
			Assert.hasText(token, invalidPathMessage);
			dataPath.add(readingIndex ? Integer.parseInt(token) : token);
			sb.delete(0, sb.length());

			readingIndex = (c == '[');
		}

		return dataPath;
	}

	/**
	 * Return the field value under the given path relative to the "data" key.
	 * @param fieldPath a field path parsed via {@link #parseFieldPath(String)}
	 * @return the field value, possibly {@code null} or {@link #NO_VALUE}
	 * @throws IllegalArgumentException in case of a mismatch between the path
	 * and the data, e.g. map or list expected vs actual value type
	 */
	@Nullable
	protected Object getFieldValue(List<Object> fieldPath) {
		Object value = (isValid() ? getData() : NO_VALUE);
		for (Object segment : fieldPath) {
			if (value == null || value == NO_VALUE) {
				return NO_VALUE;
			}
			if (segment instanceof String) {
				Assert.isTrue(value instanceof Map, () -> "Invalid path " + fieldPath + ", data: " + getData());
				Map<?, ?> map = (Map<?, ?>) value;
				value = (map.containsKey(segment) ? map.get(segment) : NO_VALUE);
			}
			else {
				Assert.isTrue(value instanceof List, () -> "Invalid path " + fieldPath + ", data: " + getData());
				int index = (int) segment;
				List<?> list = (List<?>) value;
				value = (index < list.size() ? list.get(index) : NO_VALUE);
			}
		}
		return value;
	}

	/**
	 * Return field errors whose path starts with the given field path.
	 * @param fieldPath the field path to match
	 * @return errors whose path starts with the dataPath
	 */
	protected List<GraphQLError> getFieldErrors(List<Object> fieldPath) {
		if (fieldPath.isEmpty()) {
			return Collections.emptyList();
		}
		List<GraphQLError> fieldErrors = Collections.emptyList();
		for (GraphQLError error : this.errors) {
			List<Object> errorPath = error.getPath();
			if (CollectionUtils.isEmpty(errorPath)) {
				continue;
			}
			boolean match = true;
			for (int i = 0; match && i < fieldPath.size() && i < errorPath.size(); i++) {
				match = fieldPath.get(i).equals(errorPath.get(i));
			}
			if (!match) {
				continue;
			}
			fieldErrors = (fieldErrors.isEmpty() ? new ArrayList<>() : fieldErrors);
			fieldErrors.add(error);
		}
		return fieldErrors;
	}


	@Override
	public boolean equals(Object other) {
		return (other instanceof MapGraphQlResponse &&
				this.responseMap.equals(((MapGraphQlResponse) other).responseMap));
	}

	@Override
	public int hashCode() {
		return this.responseMap.hashCode();
	}

	@Override
	public String toString() {
		return this.responseMap.toString();
	}

}

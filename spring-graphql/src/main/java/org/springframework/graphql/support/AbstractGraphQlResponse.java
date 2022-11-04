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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.ResponseField;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


/**
 * Base class for {@link GraphQlResponse} that pre-implements the ability to
 * access a {@link ResponseField}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public abstract class AbstractGraphQlResponse implements GraphQlResponse {


	@Override
	public ResponseField field(String path) {
		return new DefaultResponseField(this, path);
	}


	/**
	 * Default implementation of {@link ResponseField}.
	 */
	private static class DefaultResponseField implements ResponseField {

		private final GraphQlResponse response;

		private final String path;

		private final List<Object> parsedPath;

		@Nullable
		private final Object value;

		private final List<ResponseError> fieldErrors;


		DefaultResponseField(GraphQlResponse response, String path) {
			this.response = response;
			this.path = path;
			this.parsedPath = parsePath(path);
			this.value = initFieldValue(this.parsedPath, response);
			this.fieldErrors = initFieldErrors(path, response);
		}

		private static List<Object> parsePath(String path) {
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

		@Nullable
		private static Object initFieldValue(List<Object> path, GraphQlResponse response) {
			Object value = (response.isValid() ? response.getData() : null);
			for (Object segment : path) {
				if (value == null) {
					return null;
				}
				if (segment instanceof String) {
					Assert.isTrue(value instanceof Map, () -> "Invalid path " + path + ", data: " + response.getData());
					value = ((Map<?, ?>) value).getOrDefault(segment, null);
				}
				else {
					Assert.isTrue(value instanceof List, () -> "Invalid path " + path + ", data: " + response.getData());
					int index = (int) segment;
					value = (index < ((List<?>) value).size() ? ((List<?>) value).get(index) : null);
				}
			}
			return value;
		}

		/**
		 * Return errors whose path is at, above, or below the given path.
		 * @param path the field path to match
		 * @return errors whose path starts with the dataPath
		 */
		private static List<ResponseError> initFieldErrors(String path, GraphQlResponse response) {
			if (path.isEmpty() || response.getErrors().isEmpty()) {
				return Collections.emptyList();
			}
			return response.getErrors().stream()
					.filter(error -> {
						String errorPath = error.getPath();
						return (!errorPath.isEmpty() && (errorPath.startsWith(path) || path.startsWith(errorPath)));
					})
					.collect(Collectors.toList());
		}


		@Override
		public String getPath() {
			return this.path;
		}

		@Override
		public List<Object> getParsedPath() {
			return this.parsedPath;
		}

		@SuppressWarnings("deprecation")
		@Override
		public boolean hasValue() {
			return (this.value != null);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getValue() {
			return (T) this.value;
		}

		@SuppressWarnings("deprecation")
		@Override
		public ResponseError getError() {
			if (getValue() != null) {
				if (!this.fieldErrors.isEmpty()) {
					return this.fieldErrors.get(0);
				}
				if (!this.response.getErrors().isEmpty()) {
					return this.response.getErrors().get(0);
				}
				// No errors, set to null by DataFetcher
			}
			return null;
		}

		@Override
		public List<ResponseError> getErrors() {
			return this.fieldErrors;
		}

	}

}

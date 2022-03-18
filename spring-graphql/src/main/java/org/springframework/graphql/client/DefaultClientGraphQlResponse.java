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
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.GraphQlResponseError;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


/**
 * Default implementation of {@link ClientGraphQlResponse}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultClientGraphQlResponse extends MapGraphQlResponse implements ClientGraphQlResponse {

	private final GraphQlRequest request;

	private final Encoder<?> encoder;

	private final Decoder<?> decoder;


	DefaultClientGraphQlResponse(
			GraphQlRequest request, GraphQlResponse response, Encoder<?> encoder, Decoder<?> decoder) {

		super(response);

		this.request = request;
		this.encoder = encoder;
		this.decoder = decoder;
	}


	@Override
	public GraphQlRequest getRequest() {
		return this.request;
	}

	Encoder<?> getEncoder() {
		return this.encoder;
	}

	Decoder<?> getDecoder() {
		return this.decoder;
	}


	@Override
	public GraphQlResponseField field(String path) {
		List<Object> parsedPath = parsePath(path);
		return new DefaultGraphQlResponseField(this, path, parsedPath, getValue(parsedPath), getFieldErrors(path));
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
	private Object getValue(List<Object> path) {
		Object value = (isValid() ? getData() : null);
		for (Object segment : path) {
			if (value == null) {
				return null;
			}
			if (segment instanceof String) {
				Assert.isTrue(value instanceof Map, () -> "Invalid path " + path + ", data: " + getData());
				value = ((Map<?, ?>) value).getOrDefault(segment, null);
			}
			else {
				Assert.isTrue(value instanceof List, () -> "Invalid path " + path + ", data: " + getData());
				int index = (int) segment;
				value = (index < ((List<?>) value).size() ? ((List<?>) value).get(index) : null);
			}
		}
		return value;
	}

	/**
	 * Return field errors whose path starts with the given field path.
	 * @param path the field path to match
	 * @return errors whose path starts with the dataPath
	 */
	private List<GraphQlResponseError> getFieldErrors(String path) {
		if (path.isEmpty()) {
			return Collections.emptyList();
		}
		return getErrors().stream()
				.filter(error -> {
					String errorPath = error.getPath();
					return !errorPath.isEmpty() && (errorPath.startsWith(path) || path.startsWith(errorPath));
				})
				.collect(Collectors.toList());
	}

	@Override
	public <D> D toEntity(Class<D> type) {
		return field("").toEntity(type);
	}

	@Override
	public <D> D toEntity(ParameterizedTypeReference<D> type) {
		return field("").toEntity(type);
	}

}

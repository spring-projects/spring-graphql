/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.server;

import java.util.Locale;
import java.util.Map;

import io.rsocket.exceptions.RejectedException;
import org.jspecify.annotations.Nullable;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.graphql.GraphQlRequest} implementation for server
 * handling over RSocket.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class RSocketGraphQlRequest extends DefaultExecutionGraphQlRequest implements ExecutionGraphQlRequest {


	/**
	 * Create an instance.
	 * @param body the deserialized content of the GraphQL request
	 * @param id an identifier for the GraphQL request
	 * @param locale the locale from the HTTP request, if any
	 */
	public RSocketGraphQlRequest(Map<String, Object> body, String id, @Nullable Locale locale) {
		super(getQuery(body), getKey(OPERATION_NAME_KEY, body),
				getKey(VARIABLES_KEY, body), getKey(EXTENSIONS_KEY, body), id, locale);
	}

	@SuppressWarnings("unchecked")
	private static @Nullable <T> T getKey(String key, Map<String, Object> body) {
		return (T) body.get(key);
	}

	private static String getQuery(Map<String, Object> body) {
		String query = getKey(QUERY_KEY, body);
		if (!StringUtils.hasText(query)) {
			throw new RejectedException("No \"query\" in the request document");
		}
		return query;
	}

}

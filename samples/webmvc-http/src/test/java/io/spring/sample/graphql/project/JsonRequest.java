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
package io.spring.sample.graphql.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

class JsonRequest {

	private final String query;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;


	private JsonRequest(String query, @Nullable String operationName, Map<String, Object> variables) {
		this.query = query;
		this.operationName = operationName;
		this.variables = (!CollectionUtils.isEmpty(variables) ?
				new LinkedHashMap<>(variables) : Collections.emptyMap());
	}


	public String getQuery() {
		return query;
	}

	public String query() {
		return this.query;
	}

	@Nullable
	public String operationName() {
		return this.operationName;
	}

	public Map<String, Object> variables() {
		return this.variables;
	}

	public static JsonRequest create(String query) {
		return new JsonRequest(query, null, Collections.emptyMap());
	}

	public static JsonRequest create(String query, String operationName, Map<String, Object> variables) {
		return new JsonRequest(query, operationName, variables);
	}

}

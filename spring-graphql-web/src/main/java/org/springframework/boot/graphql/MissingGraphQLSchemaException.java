/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.boot.graphql;

import org.springframework.util.StringUtils;

/**
 * Exception thrown when no GraphQL schema is available.
 */
public class MissingGraphQLSchemaException extends RuntimeException {

	private final String path;

	MissingGraphQLSchemaException(String path) {
		super(StringUtils.hasText(path) ? "Path to GraphQL schema not configured" : "Cannot find schema file at: "
				+ path + " (please add a schema file or check your GraphQL configuration)");
		this.path = path;
	}

	public String getPath() {
		return this.path;
	}
}

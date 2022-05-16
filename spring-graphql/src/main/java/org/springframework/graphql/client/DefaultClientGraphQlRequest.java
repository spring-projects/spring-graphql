/*
 * Copyright 2020-2022 the original author or authors.
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


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.lang.Nullable;

/**
 * Default implementation of {@link ClientGraphQlRequest}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultClientGraphQlRequest extends DefaultGraphQlRequest implements ClientGraphQlRequest {

	private final Map<String, Object> attributes = new ConcurrentHashMap<>();


	DefaultClientGraphQlRequest(
			String document, @Nullable String operationName,
			Map<String, Object> variables, Map<String, Object> extensions,
			Map<String, Object> attributes) {

		super(document, operationName, variables, extensions);
		this.attributes.putAll(attributes);
	}


	@Override
	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

}

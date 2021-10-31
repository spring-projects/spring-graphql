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
package org.springframework.graphql.test.tester;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.graphql.RequestInput;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Base class support for implementations of
 * {@link GraphQlTester.RequestSpec} and {@link WebGraphQlTester.RequestSpec}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class GraphQlTesterRequestSpecSupport {

	private final String query;

	@Nullable
	private String operationName;

	private final Map<String, Object> variables = new LinkedHashMap<>();

	@Nullable
	private Locale locale;


	protected GraphQlTesterRequestSpecSupport(String query) {
		Assert.notNull(query, "`query` is required");
		this.query = query;
	}


	protected void setOperationName(@Nullable String name) {
		this.operationName = name;
	}

	protected void addVariable(String name, @Nullable Object value) {
		this.variables.put(name, value);
	}

	protected void setLocale(Locale locale) {
		this.locale = locale;
	}

	protected void verify(GraphQlTester.ResponseSpec responseSpec) {
		responseSpec.path("$.errors").valueIsEmpty();
	}

	protected RequestInput createRequestInput() {
		return new RequestInput(this.query, this.operationName, this.variables, this.locale);
	}

}

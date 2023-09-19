/*
 * Copyright 2002-2023 the original author or authors.
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

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebInputException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WebGraphQlRequest}.
 *
 * @author Rossen Stoyanchev
 */
public class WebGraphQlRequestTests {

	@Test  // gh-726
	void invalidBody() {
		testInvalidBody(new HashMap<>());

		Map<String, Object> empty = new HashMap<>();
		empty.put("query", Collections.emptyMap());
		testInvalidBody(empty);

		Map<String, Object> emptyOperations = new HashMap<>();
		emptyOperations.put("query", "query { foo }");
		emptyOperations.put("operationName", Collections.emptyMap());
		testInvalidBody(emptyOperations);

		Map<String, Object> invalidVariables = new HashMap<>();
		invalidVariables.put("query", "query { foo }");
		invalidVariables.put("variables", "not-a-map");
		testInvalidBody(invalidVariables);

		Map<String, Object> invalidExtensions = new HashMap<>();
		invalidExtensions.put("query", "query { foo }");
		invalidExtensions.put("extensions", "not-a-map");
		testInvalidBody(invalidExtensions);
	}

	private void testInvalidBody(Map<String, Object> body) {
		assertThatThrownBy(() ->
				new WebGraphQlRequest(
						URI.create("/graphql"), new HttpHeaders(), body, "1", null))
				.isInstanceOf(ServerWebInputException.class);
	}


}

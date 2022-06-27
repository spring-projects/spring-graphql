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

package org.springframework.graphql.support;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultGraphQlRequest}.
 * @author Brian Clozel
 */
class DefaultGraphQlRequestTests {

	@Test
	void requestAsMapShouldContainAllEntries() {
		String document = "query HeroNameAndFriends($episode: Episode) {" +
				"  hero(episode: $episode) {" +
				"    name"
				+ "  }" +
				"}";
		Map<String, Object> variables = Collections.singletonMap("episode", "JEDI");
		Map<String, Object> extensions = Collections.singletonMap("myExtension", "value");

		DefaultExecutionGraphQlRequest request = new DefaultExecutionGraphQlRequest(document, "HeroNameAndFriends",
				variables, extensions, "1", null);

		assertThat(request.toMap()).containsEntry("query", document).containsEntry("operationName", "HeroNameAndFriends")
				.containsEntry("variables", variables).containsEntry("extensions", extensions);
	}

}
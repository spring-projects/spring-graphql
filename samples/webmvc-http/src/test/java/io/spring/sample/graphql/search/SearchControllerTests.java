/*
 * Copyright 2021 the original author or authors.
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
package io.spring.sample.graphql.search;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.boot.test.tester.AutoConfigureWebGraphQlTester;
import org.springframework.graphql.test.tester.WebGraphQlTester;

import org.junit.jupiter.api.Test;

@SpringBootTest
@AutoConfigureWebGraphQlTester
class SearchControllerTests {

	@Autowired
	private WebGraphQlTester graphQlTester;

	@Test
	void fullResults() {
		this.graphQlTester.queryName("search")
				.variable("text", "spring")
				.execute()
				.path("search[*].name")
				.entityList(String.class).hasSizeGreaterThan(3);
	}

	@Test
	void artifactRepositoriesResults() {
		this.graphQlTester.queryName("search")
				.variable("text", "spring")
				.variable("type", SearchType.ARTIFACTREPOSITORY)
				.execute()
				.path("search[*].name")
				.entityList(String.class).hasSize(3);
	}

	@Test
	void projectsResults() {
		this.graphQlTester.queryName("search")
				.variable("text", "spring")
				.variable("type", SearchType.PROJECT)
				.execute()
				.path("search[*].name")
				.entityList(String.class).hasSizeGreaterThan(3);
	}
}

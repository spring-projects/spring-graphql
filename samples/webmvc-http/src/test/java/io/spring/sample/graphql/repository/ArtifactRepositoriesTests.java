/*
 * Copyright 2020-2021 the original author or authors.
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

package io.spring.sample.graphql.repository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;

@SpringBootTest
@AutoConfigureGraphQlTester
class ArtifactRepositoriesTests {

	@Autowired
	private GraphQlTester graphQlTester;
	
	@Test
	void querydslRepositorySingle() {
		this.graphQlTester.documentName("artifactRepository")
				.variable("id", "spring-releases")
				.execute()
				.path("artifactRepository.name")
				.entity(String.class).isEqualTo("Spring Releases");
	}

	@Test
	void querydslRepositoryMany() {
		this.graphQlTester.documentName("artifactRepositories")
				.execute()
				.path("artifactRepositories[*].id")
				.entityList(String.class).containsExactly("spring-releases", "spring-milestones");
	}

}
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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.boot.test.tester.AutoConfigureGraphQlTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphQL requests via {@link GraphQlTester} connecting to {@link MockMvc}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureGraphQlTester
public class MockMvcGraphQlTests {

	@Autowired
	private GraphQlTester graphQlTester;

	@Test
	void jsonPath() {
		String query = "{" +
				"  project(slug:\"spring-framework\") {" +
				"    releases {" +
				"      version" +
				"    }"+
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.path("project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(1);

	}

	@Test
	void jsonContent() {
		String query = "{" +
				"  project(slug:\"spring-framework\") {" +
				"    repositoryUrl" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.path("project")
				.matchesJson("{\"repositoryUrl\":\"http://github.com/spring-projects/spring-framework\"}");
	}

	@Test
	void decodedResponse() {
		String query = "{" +
				"  project(slug:\"spring-framework\") {" +
				"    releases {" +
				"      version" +
				"    }"	+
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.path("project")
				.entity(Project.class)
				.satisfies(project -> assertThat(project.getReleases()).hasSizeGreaterThan(1));
	}

}

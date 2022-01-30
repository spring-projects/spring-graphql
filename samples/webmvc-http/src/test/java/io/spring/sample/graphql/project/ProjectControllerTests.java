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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.GraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * GraphQL requests via {@link GraphQlTester}.
 */
@GraphQlTest(ProjectController.class)
public class ProjectControllerTests {

	@Autowired
	private GraphQlTester graphQlTester;

	@MockBean
	private SpringProjectsClient projectsClient;

	private Project springFramework;

	private Release latestRelease;

	@BeforeEach
	void setup() {
		this.springFramework = new Project("spring-framework", "Spring Framework",
				"http://github.com/spring-projects/spring-framework", ProjectStatus.ACTIVE);
		this.latestRelease = new Release(this.springFramework, "5.3.0", ReleaseStatus.GENERAL_AVAILABILITY);
	}

	@Test
	void jsonPath() {
		given(this.projectsClient.fetchProject(eq("spring-framework"))).willReturn(this.springFramework);
		given(this.projectsClient.fetchProjectReleases(eq("spring-framework"))).willReturn(Collections.singletonList(this.latestRelease));
		this.graphQlTester.operationName("projectReleases")
				.variable("slug", "spring-framework")
				.execute()
				.path("project.releases[*].version")
				.entityList(String.class)
				.hasSize(1);

	}

	@Test
	void jsonContent() {
		given(this.projectsClient.fetchProject(eq("spring-framework"))).willReturn(this.springFramework);
		this.graphQlTester.operationName("projectRepositoryUrl")
				.variable("slug", "spring-framework")
				.execute()
				.path("project")
				.matchesJson("{\"repositoryUrl\":\"http://github.com/spring-projects/spring-framework\"}");
	}

	@Test
	void decodedResponse() {
		given(this.projectsClient.fetchProject(eq("spring-framework"))).willReturn(this.springFramework);
		given(this.projectsClient.fetchProjectReleases(eq("spring-framework"))).willReturn(Collections.singletonList(this.latestRelease));
		this.graphQlTester.operationName("projectReleases")
				.variable("slug", "spring-framework")
				.execute()
				.path("project")
				.entity(Project.class)
				.satisfies(project -> assertThat(project.getReleases()).hasSize(1));
	}

}

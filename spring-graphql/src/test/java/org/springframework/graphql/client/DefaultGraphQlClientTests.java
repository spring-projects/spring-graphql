/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.RequestInput;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultGraphQlClientTests {

	@Test
	void executeQuery() {
		String query = "{" +
				"  project(slug: \"spring-framework\") {" +
				"    slug" +
				"    name" +
				"    repositoryUrl" +
				"  }" +
				"}";

		Project expectedProject = new Project(
				"spring-framework", "Spring Framework", "https://github.com/spring-projects/spring-framework");

		ExecutionResultImpl result = ExecutionResultImpl.newExecutionResult()
				.data(Collections.singletonMap("project", expectedProject.toMap()))
				.build();

		TestTransport transport = new TestTransport(result);

		Project project = GraphQlClient.builder(transport).build()
				.operation(query)
				.execute()
				.map(spec -> spec.toEntity("project", Project.class))
				.block();

		assertThat(project).isNotNull();
		assertThat(project.getSlug()).isEqualTo(expectedProject.getSlug());
		assertThat(project.getName()).isEqualTo(expectedProject.getName());
		assertThat(project.getRepositoryUrl()).isEqualTo(expectedProject.getRepositoryUrl());
	}


	private static class TestTransport implements GraphQlTransport {

		private final Mono<ExecutionResult> response;

		@Nullable
		private RequestInput savedRequestInput;

		public TestTransport(ExecutionResult response) {
			this(Mono.just(response));
		}

		public TestTransport(Mono<ExecutionResult> response) {
			this.response = response;
		}

		public RequestInput getSavedRequestInput() {
			Assert.notNull(this.savedRequestInput, "No saved RequestInput");
			return this.savedRequestInput;
		}

		@Override
		public Mono<ExecutionResult> execute(RequestInput input) {
			this.savedRequestInput = input;
			return this.response;
		}

		@Override
		public Flux<ExecutionResult> executeSubscription(RequestInput input) {
			throw new UnsupportedOperationException();
		}
	}


	private static class Project {

		private String slug;

		private String name;

		private String repositoryUrl;

		public Project() {
		}

		public Project(String slug, String name, String repositoryUrl) {
			this.slug = slug;
			this.name = name;
			this.repositoryUrl = repositoryUrl;
		}

		public String getSlug() {
			return this.slug;
		}

		public void setSlug(String slug) {
			this.slug = slug;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getRepositoryUrl() {
			return this.repositoryUrl;
		}

		public void setRepositoryUrl(String repositoryUrl) {
			this.repositoryUrl = repositoryUrl;
		}

		public Map<String, Object> toMap() {
			Map<String, Object> map = new HashMap<>();
			map.put("slug", getSlug());
			map.put("name", getName());
			map.put("repositoryUrl", getRepositoryUrl());
			return map;
		}

	}

}

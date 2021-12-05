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

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import io.spring.sample.graphql.project.Project;
import io.spring.sample.graphql.project.SpringProjectsClient;
import io.spring.sample.graphql.repository.ArtifactRepositories;
import io.spring.sample.graphql.repository.ArtifactRepository;

@Controller
public class SearchController {

	private final SpringProjectsClient client;
	private final ArtifactRepositories repositories;

	public SearchController(SpringProjectsClient client, ArtifactRepositories repositories) {
		this.client = client;
		this.repositories = repositories;
	}

	@QueryMapping
	public Stream<Object> search(@Argument String text, @Argument SearchType type) {
		Stream<ArtifactRepository> repos =
			(type == null || type == SearchType.ARTIFACTREPOSITORY) ?
				StreamSupport.stream(repositories.findAll().spliterator(), false)
					.filter(r -> r.getName().toLowerCase().contains(text.toLowerCase())) :
				Stream.empty();
		Stream<Project> projects =
			(type == null || type == SearchType.PROJECT) ?
				client.fetchAllProjects().stream()
					.filter(p -> p.getName().toLowerCase().contains(text.toLowerCase())) :
				Stream.empty();
		return Stream.concat(repos, projects);
	}
}

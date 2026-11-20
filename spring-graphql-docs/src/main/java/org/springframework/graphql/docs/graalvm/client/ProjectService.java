/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.graalvm.client;

import reactor.core.publisher.Mono;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.stereotype.Component;

@Component
@RegisterReflectionForBinding(Project.class) // <2>
public class ProjectService {

	private final GraphQlClient graphQlClient;

	public ProjectService(GraphQlClient graphQlClient) {
		this.graphQlClient = graphQlClient;
	}

	public Mono<Project> project(String projectSlug) {
		String document = """
				query projectWithReleases($projectSlug: ID!) {
					project(slug: $projectSlug) {
						name
						releases {
							version
						}
					}
				}
				""";

		return this.graphQlClient.document(document)
				.variable("projectSlug", projectSlug)
				.retrieve("project")
				.toEntity(Project.class); // <1>
	}
}

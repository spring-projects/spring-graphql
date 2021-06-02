package io.spring.sample.graphql.project;

import graphql.schema.idl.RuntimeWiring;
import org.springframework.graphql.boot.RuntimeWiringCustomizer;
import org.springframework.stereotype.Component;

@Component
public class ProjectDataWiring implements RuntimeWiringCustomizer {

	private final SpringProjectsClient client;

	public ProjectDataWiring(SpringProjectsClient client) {
		this.client = client;
	}

	@Override
	public void customize(RuntimeWiring.Builder builder) {
		builder.type("Query", typeWiring -> typeWiring.dataFetcher("project", env -> {
			String slug = env.getArgument("slug");
			return client.fetchProject(slug);
		})).type("Project", typeWiring -> typeWiring.dataFetcher("releases", env -> {
			Project project = env.getSource();
			return client.fetchProjectReleases(project.getSlug());
		}));
	}

}

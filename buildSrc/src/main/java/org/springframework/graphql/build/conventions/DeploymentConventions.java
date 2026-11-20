/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.build.conventions;

import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

/**
 * Convention that configures a local deployment Maven repository on the CI
 * so that the Concourse resource can later push it to a remote repository.
 *
 * @author Brian Clozel
 */
public class DeploymentConventions {

	public void apply(Project project) {
		project.getPlugins().apply(MavenPublishPlugin.class);
		project.getPlugins().withType(MavenPublishPlugin.class).forEach((mavenPublishPlugin) -> {
			PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
			if (project.hasProperty("deploymentRepository")) {
				publishing.getRepositories().maven((mavenArtifactRepository) -> {
					mavenArtifactRepository.setUrl(project.property("deploymentRepository"));
					mavenArtifactRepository.setName("deployment");
				});
			}
		});
	}
}

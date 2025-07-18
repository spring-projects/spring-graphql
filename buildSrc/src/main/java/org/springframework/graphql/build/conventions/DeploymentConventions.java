/*
 * Copyright 2020-present the original author or authors.
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

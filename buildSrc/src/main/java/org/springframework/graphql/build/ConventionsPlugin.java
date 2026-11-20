/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

import org.springframework.graphql.build.conventions.DeploymentConventions;
import org.springframework.graphql.build.conventions.FormattingConventions;
import org.springframework.graphql.build.conventions.JavaConventions;
import org.springframework.graphql.build.conventions.KotlinConventions;

/**
 * Plugin to apply conventions to projects that are part of Spring Framework's build.
 * Conventions are applied in response to various plugins being applied.
 *
 * When the {@link JavaBasePlugin} is applied, the conventions in {@link JavaConventions}
 * are applied.
 * When the {@link KotlinBasePlugin} is applied, the conventions in {@link KotlinConventions}
 * are applied.
 * The conventions in {@link DeploymentConventions} apply and configure the {@link MavenPublishPlugin}.
 *
 * @author Brian Clozel
 */
public class ConventionsPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		new FormattingConventions().apply(project);
		new JavaConventions().apply(project);
		new KotlinConventions().apply(project);
		new DeploymentConventions().apply(project);
	}
}

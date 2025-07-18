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

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.build.conventions;

import io.spring.javaformat.gradle.SpringJavaFormatPlugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Conventions that are applied in the presence of the {@link JavaBasePlugin}. When the
 * plugin is applied, {@link CheckstylePlugin Checkstyle} is applied and configured.
 *
 * @author Brian Clozel
 */
public class FormattingConventions {

	public void apply(Project project) {
		project.getPlugins().withType(JavaBasePlugin.class, (java) -> applySpringJavaFormat(project));
	}

	private void applySpringJavaFormat(Project project) {
		project.getPlugins().apply(CheckstylePlugin.class);
		CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
		checkstyle.setToolVersion("10.12.4");
		checkstyle.getConfigDirectory().set(project.getRootProject().file("src/checkstyle"));
		String version = SpringJavaFormatPlugin.class.getPackage().getImplementationVersion();
		DependencySet checkstyleDependencies = project.getConfigurations().getByName("checkstyle").getDependencies();
		checkstyleDependencies
				.add(project.getDependencies().create("com.puppycrawl.tools:checkstyle:" + checkstyle.getToolVersion()));
		checkstyleDependencies
				.add(project.getDependencies().create("io.spring.javaformat:spring-javaformat-checkstyle:" + version));
		includeKotlinSources(project);
	}

	private void includeKotlinSources(Project project) {
		SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
		sourceSets.all((sourceSet) -> project.getTasks().named(sourceSet.getTaskName("checkstyle", null), Checkstyle.class,
				(task) -> task.source(project.fileTree("src/" + sourceSet.getName() + "/kotlin",
						(tree) -> tree.include("**/*.kt")))));
	}

}

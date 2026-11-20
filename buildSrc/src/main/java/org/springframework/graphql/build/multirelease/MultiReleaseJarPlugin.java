/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.build.multirelease;

import javax.inject.Inject;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * A plugin which adds support for building multi-release jars
 * with Gradle.
 * @author Cedric Champeau
 * @author Brian Clozel
 * @see <a href="https://github.com/melix/mrjar-gradle-plugin">original project</a>
 */
public class MultiReleaseJarPlugin implements Plugin<Project> {

	@Inject
	protected JavaToolchainService getToolchains() {
		throw new UnsupportedOperationException();
	}

	public void apply(Project project) {
		project.getPlugins().apply(JavaPlugin.class);
		ExtensionContainer extensions = project.getExtensions();
		JavaPluginExtension javaPluginExtension = extensions.getByType(JavaPluginExtension.class);
		ConfigurationContainer configurations = project.getConfigurations();
		TaskContainer tasks = project.getTasks();
		DependencyHandler dependencies = project.getDependencies();
		ObjectFactory objects = project.getObjects();
		extensions.create("multiRelease", MultiReleaseExtension.class,
				javaPluginExtension.getSourceSets(),
				configurations,
				tasks,
				dependencies,
				objects);
	}
}

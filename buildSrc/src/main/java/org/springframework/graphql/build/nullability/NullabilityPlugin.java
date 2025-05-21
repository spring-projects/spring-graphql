/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.build.nullability;

import java.util.function.Consumer;
import java.util.regex.Pattern;

import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * {@link Plugin} for enforcing Nullability checks on the source code.
 *
 * @author Brian Clozel
 */
public class NullabilityPlugin implements Plugin<Project> {

	private static final Pattern COMPILE_MAIN_SOURCES_TASK_NAME = Pattern.compile("compile(\\d+)?Java");

	@Override
	public void apply(Project project) {
		project.getPlugins().apply(ErrorPronePlugin.class);
		DependencySet errorproneConfig = project.getConfigurations().getByName("errorprone").getDependencies();
		errorproneConfig.add(project.getDependencies().create("com.uber.nullaway:nullaway:0.12.6"));
		errorproneConfig.add(project.getDependencies().create("com.google.errorprone:error_prone_core:2.37.0"));

		project.getTasks()
				.withType(JavaCompile.class)
				.configureEach((javaCompile) -> {
					if (compilesMainSources(javaCompile)) {
						doWithErrorProneOptions(javaCompile, (errorProneOptions) -> {
							errorProneOptions.getDisableAllChecks().set(true);
							errorProneOptions.option("NullAway:OnlyNullMarked", "true");
							errorProneOptions.option("NullAway:CustomContractAnnotations", "org.springframework.lang.Contract");
							errorProneOptions.option("NullAway:JSpecifyMode", "true");
							errorProneOptions.error("NullAway");
						});
					}
					else {
						doWithErrorProneOptions(javaCompile, (errorProneOptions) -> {
							errorProneOptions.getEnabled().set(false);
						});
					}
				});
	}

	private boolean compilesMainSources(JavaCompile compileTask) {
		return COMPILE_MAIN_SOURCES_TASK_NAME.matcher(compileTask.getName()).matches();
	}

	private void doWithErrorProneOptions(JavaCompile compileTask, Consumer<ErrorProneOptions> optionsConsumer) {
		CompileOptions options = compileTask.getOptions();
		ErrorProneOptions errorProneOptions = ((ExtensionAware) options).getExtensions().getByType(ErrorProneOptions.class);
		optionsConsumer.accept(errorProneOptions);
	}

}

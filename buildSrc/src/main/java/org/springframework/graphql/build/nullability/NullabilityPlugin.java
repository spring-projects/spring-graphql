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

import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * {@link Plugin} for enforcing Nullability checks on the source code.
 *
 * @author Brian Clozel
 */
public class NullabilityPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		new ErrorPronePlugin(project.getProviders()).apply(project);
		DependencySet errorproneConfig = project.getConfigurations().getByName("errorprone").getDependencies();
		errorproneConfig.add(project.getDependencies().create("com.uber.nullaway:nullaway:0.12.6"));
		errorproneConfig.add(project.getDependencies().create("com.google.errorprone:error_prone_core:2.37.0"));

		project.getTasks().withType(JavaCompile.class).configureEach((javaCompile) -> {
			CompileOptions options = javaCompile.getOptions();
			ErrorProneOptions errorProneOptions = ((ExtensionAware) options).getExtensions().getByType(ErrorProneOptions.class);
			errorProneOptions.getDisableAllChecks().set(true);
			errorProneOptions.option("NullAway:OnlyNullMarked", "true");
			errorProneOptions.option("NullAway:CustomContractAnnotations", "org.springframework.lang.Contract");
			errorProneOptions.option("NullAway:JSpecifyMode", "true");
			errorProneOptions.error("NullAway");
		});

	}

}

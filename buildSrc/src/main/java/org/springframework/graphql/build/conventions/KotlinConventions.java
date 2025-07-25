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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget;
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion;
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile;

/**
 * Convention for compiling Kotlin code.
 * @author Brian Clozel
 */
public class KotlinConventions {

	public void apply(Project project) {
		project.getPlugins().withId("org.jetbrains.kotlin.jvm",
				(plugin) -> project.getTasks().withType(KotlinCompile.class, this::configure));
	}

	private void configure(KotlinCompile compile) {
		compile.compilerOptions(options -> {
			options.getApiVersion().set(KotlinVersion.KOTLIN_2_1);
			options.getLanguageVersion().set(KotlinVersion.KOTLIN_2_1);
			options.getJvmTarget().set(JvmTarget.JVM_17);
			options.getJavaParameters().set(true);
			options.getAllWarningsAsErrors().set(true);
			options.getFreeCompilerArgs().addAll(
					"-Xsuppress-version-warnings",
					"-Xjsr305=strict", // For dependencies using JSR 305
					"-opt-in=kotlin.RequiresOptIn",
					"-Xjdk-release=17" // Needed due to https://youtrack.jetbrains.com/issue/KT-49746
			);
		});
	}

}

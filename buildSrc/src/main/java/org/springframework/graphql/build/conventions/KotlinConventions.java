/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
			options.getApiVersion().set(KotlinVersion.KOTLIN_2_2);
			options.getLanguageVersion().set(KotlinVersion.KOTLIN_2_2);
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

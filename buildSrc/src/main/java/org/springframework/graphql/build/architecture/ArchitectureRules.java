/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.build.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

abstract class ArchitectureRules {

	static ArchRule allPackagesShouldBeFreeOfTangles() {
		return SlicesRuleDefinition.slices().matching("(**)").should().beFreeOfCycles();
	}

	static ArchRule noClassesShouldCallStringToLowerCaseWithoutLocale() {
		return ArchRuleDefinition.noClasses()
				.should()
				.callMethod(String.class, "toLowerCase")
				.because("String.toLowerCase(Locale.ROOT) should be used instead");
	}

	static ArchRule noClassesShouldCallStringToUpperCaseWithoutLocale() {
		return ArchRuleDefinition.noClasses()
				.should()
				.callMethod(String.class, "toUpperCase")
				.because("String.toUpperCase(Locale.ROOT) should be used instead");
	}

	static ArchRule packageInfoShouldBeNullMarked() {
		return ArchRuleDefinition.classes()
				.that().haveSimpleName("package-info")
				.should().beAnnotatedWith("org.jspecify.annotations.NullMarked")
				.allowEmptyShould(true);
	}

	static ArchRule classShouldNotUseSpringNullAnnotations() {
		return ArchRuleDefinition.noClasses()
				.should().dependOnClassesThat()
				.haveFullyQualifiedName("org.jspecify.annotations.NonNull")
				.orShould().dependOnClassesThat()
				.haveFullyQualifiedName("org.jspecify.annotations.Nullable");
	}


	static ArchRule classesShouldNotImportForbiddenTypes() {
		return ArchRuleDefinition.noClasses()
				.should().dependOnClassesThat()
				.haveFullyQualifiedName("reactor.core.support.Assert")
				.orShould().dependOnClassesThat()
				.haveFullyQualifiedName("org.slf4j.LoggerFactory");
	}

	static ArchRule javaClassesShouldNotImportKotlinAnnotations() {
		return ArchRuleDefinition.noClasses()
				.that(new DescribedPredicate<JavaClass>("is not a Kotlin class") {
						  @Override
						  public boolean test(JavaClass javaClass) {
							  return javaClass.getSourceCodeLocation()
									  .getSourceFileName().endsWith(".java");
						  }
					  }
				)
				.should().dependOnClassesThat()
				.resideInAnyPackage("org.jetbrains.annotations..")
				.allowEmptyShould(true);
	}

}

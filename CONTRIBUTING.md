# Contributing to Spring for GraphQL

First off, thank you for taking the time to contribute! :+1: :tada:

Spring for GraphQL is released under the Apache 2.0 license.
If you would like to contribute something, or want to hack on the code, this document should help you get started.

### Table of Contents

* [Code of Conduct](#code-of-conduct)
* [How to Contribute](#how-to-contribute)
  * [Research First](#research-first)
  * [Create an Issue](#create-an-issue)
  * [Issue Lifecycle](#issue-lifecycle)
  * [Submit a Pull Request](#submit-a-pull-request)
  * [Commit Messages](#commit-messages)
  * [Participate in Reviews](#participate-in-reviews)
* [Build from Source](#build-from-source)
* [Source Code Style](#source-code-style)
* [Tests](#tests)
* [Reference Docs](#reference-docs)

### Code of Conduct

This project is governed by the [Spring Code of Conduct](CODE_OF_CONDUCT.adoc).
By participating you are expected to uphold this code.
Please report unacceptable behavior to spring-code-of-conduct@spring.io.

**We accept contributions created with the help of AI coding agents, but they must be carefully reviewed by a human who remains accountable for the quality of the contribution.**
Contributions submitted by GitHub accounts controlled by autonomous AI bots are forbidden and will result in permanent bans.

### How to Contribute

#### Research First

If you believe there is an issue, search through [existing issues](https://github.com/spring-projects/spring-graphql/issues) and [pull requests](https://github.com/spring-projects/spring-graphql/pulls), trying a few different ways to find discussions, past or current, that are related to the issue.
Reading those discussions helps you to learn about the issue, and helps us to make a decision.

#### Create an Issue

Reporting an issue or making a feature request is a great way to contribute.
Your feedback and the conversations that result from it provide a continuous flow of ideas.
However, before creating a ticket, please take the time to [research](#research-first) first.

The issue tracker is an important place of record for design discussions and should be self-sufficient: please provide a complete description in the issue instead of simply referring to an external discussion or conversation.

Once you're ready, create an issue on [GitHub](https://github.com/spring-projects/spring-graphql/issues).
Please submit issues against [supported versions](https://spring.io/projects/spring-graphql#support).

Many issues are caused by subtle behavior, typos, and unintended configuration.
Creating a Minimal Reproducible Example (starting with https://start.spring.io for example) of the problem helps the team quickly triage your issue and get to the core of the problem.
Share it as a link to a source repository or attach it as an archive, along with instructions on how to reproduce the problem.

For enhancement requests, before explaining how you would like things to work, please describe a concrete use case for the feature and how you have tried to solve it so far.

#### Issue Lifecycle

When an issue is first created, it is flagged `status: waiting-for-triage` waiting for a team member to triage it.
Once the issue has been reviewed, the team may ask for further information if needed, and based on the findings, the issue is either assigned a target milestone or is closed with a specific status.

When a fix is ready, the issue is closed and may still be re-opened until the fix is released.
After that the issue will typically no longer be reopened.
In rare cases if the issue was not at all fixed, the issue may be re-opened.
In most cases however any follow-up reports will need to be created as new issues with a fresh description.

#### Submit a Pull Request

1. Should you create an issue first? No, just create the pull request and use the description to provide context and motivation, as you would for an issue.
If you want to start a discussion first or have already created an issue, once a pull request is created, we will close the issue as superseded by the pull request, and the discussion about the issue will continue under the pull request.

1. Always check out the `main` branch and submit pull requests against it (for the target version see [gradle.properties](gradle.properties)).
Backports to prior versions will be considered on a case-by-case basis and reflected as the fix version in the issue tracker.

1. Please do not submit pull requests:
   * With GitHub accounts managed by autonomous AI bots.
   * For issues with the label `status: waiting-for-triage`, which indicates that the team has not yet triaged or decided on the issue.
   * For issues already assigned to someone else, since the assignee is working on it or plans to.
   * For straightforward or polish-style changes.

1. Before submitting a pull request:
   * Add new tests, or update existing ones, that exercise the code you have added or modified.
   * Run the build and tests for the affected modules locally (see [Build from Source](#build-from-source)), including the `checkstyle` checks.
   * Carefully review the changes yourself, especially when they were generated by a coding agent.

1. Choose the granularity of your commits consciously and squash commits that represent multiple edits or corrections of the same logical change.
See [Rewriting History section of Pro Git](https://git-scm.com/book/en/Git-Tools-Rewriting-History) for an overview of streamlining the commit history.

1. All commits must include a _Signed-off-by_ trailer at the end of each commit message to indicate that the contributor agrees to the Developer Certificate of Origin.
For additional details, please refer to the blog post [Hello DCO, Goodbye CLA: Simplifying Contributions to Spring](https://spring.io/blog/2025/01/06/hello-dco-goodbye-cla-simplifying-contributions-to-spring).

1. Format commit messages as described in [Commit Messages](#commit-messages).

1. If there is a prior issue, reference the GitHub issue number in the description of the pull request, not in its title.

If accepted, your contribution may be heavily modified as needed prior to merging.
You will likely retain author attribution for your Git commits granted that the bulk of your changes remain intact.
You may also be asked to rework the submission.

If asked to make changes, simply push the changes against the same branch, and your pull request will be updated.
In other words, you do not need to create a new pull request when asked to make changes.

#### Commit Messages

A commit message consists of a subject line, a blank line, a description, and trailers.

* The subject line should be concise (about 60 characters at most), start with a capitalized verb in the imperative mood, and not end with a period.
It should not include prefixes like `fix:`, `feat:` or `docs:`, nor the issue or pull request number.
* The description should be wrapped at 72 characters and explain the motivation for the change, for example with "Prior to this commit, ..." followed by "This commit ...".
* The description is followed by a reference to the related issue, for example `Closes gh-1234` (which closes the issue once the commit is merged) or `See gh-1234` (which only references it).
* The last line is the `Signed-off-by` trailer, which `git commit -s` adds for you.

For example:

```text
Fix argument resolution for nullable Optional parameters

Prior to this commit, a missing argument bound to an `Optional`
parameter was resolved as `null` instead of an empty `Optional`.

This commit wraps missing values so that `Optional` parameters
always receive a non-null value.

Closes gh-1234

Signed-off-by: Firstname Lastname <username@users.noreply.github.com>
```

To avoid mentioning unrelated GitHub users, annotation names such as `@Override` should not appear verbatim in commit messages or pull request titles.
Enclose them in backticks instead, as in `` `@Override` ``.

See the [Commit Guidelines section of Pro Git](https://git-scm.com/book/en/Distributed-Git-Contributing-to-a-Project#Commit-Guidelines) for best practices around commit messages, and use `git log` to see some examples.

#### Participate in Reviews

Helping to review pull requests is another great way to contribute.
Your feedback can help to shape the implementation of new features.
When reviewing pull requests, however, please refrain from approving or rejecting a PR unless you are a core committer for Spring for GraphQL.

### Build from Source

Spring for GraphQL uses a [Gradle](https://gradle.org) build.
The instructions below use the Gradle Wrapper from the root of the source tree.
The wrapper script serves as a cross-platform, self-contained bootstrap mechanism for the build system.

#### Before You Start

To build you will need [Git](https://docs.github.com/en/get-started/quickstart/set-up-git) and the JDK specified in the [.sdkmanrc](.sdkmanrc) file (currently JDK 25, for example [Liberica JDK 25](https://bell-sw.com/pages/downloads/#jdk-25)).
The build produces artifacts compatible with Java 17+, and a Java 21 multi-release source set for the `spring-graphql` module.

If you use [SDKMAN!](https://sdkman.io/), run `sdk env install` to install the JDK specified in `.sdkmanrc`, and `sdk env` to use it in your current shell.

#### Get the Source Code

```shell
git clone git@github.com:spring-projects/spring-graphql.git
cd spring-graphql
```

#### Build from the Command Line

To compile, test, and build all jars and docs use:

```shell
./gradlew build
```

The first time you run the build it may take a while to download Gradle and all build dependencies, as well as to run all tests.
Once you've bootstrapped a Gradle distribution and downloaded dependencies, those are cached in your `$HOME/.gradle` directory.
Some tests rely on [Testcontainers](https://testcontainers.com/) and require a running container runtime.

Gradle has good incremental build support, so run without `clean` to keep things snappy.
You can also use the `:<project>` prefix to only run the tasks of a given module and the modules it depends on.
For example, if iterating over changes in `spring-graphql`, run the following to build and test that module:

```shell
./gradlew :spring-graphql:test
```

To run a single test class or method, use the `--tests` option:

```shell
./gradlew :spring-graphql:test --tests org.springframework.graphql.execution.DefaultExecutionGraphQlServiceTests
```

To run all checks for a module, including tests, `checkstyle` and architecture checks, use:

```shell
./gradlew :spring-graphql:check
```

#### Install in Local Maven Repository

If you need to publish Spring for GraphQL artifacts locally for testing, you can do the following:

```shell
./gradlew publishToMavenLocal
```

### Source Code Style

This section defines the coding standards for source files in Spring for GraphQL.
Many of these rules are enforced at build time by [Checkstyle](src/checkstyle/checkstyle.xml), which is based on the [Spring Java Format](https://github.com/spring-io/spring-javaformat) rules, and by architecture checks relying on [ArchUnit](https://www.archunit.org/).

Above all, please carefully follow the whitespace and formatting conventions already present in the code you are modifying, and do not reformat code for its own sake.

#### Source File Basics

* Source files must be encoded using `UTF-8`.
* Indentation uses _tabs_, not spaces.
* Use Unix (LF), not DOS (CRLF), line endings.
* Eliminate all trailing whitespace.

#### Source File Structure

A source file consists of the following, in this exact order:

* License
* Package statement
* Import statements
* Exactly one top-level class

Exactly one blank line separates each of the above sections.

##### License

Each Java and Kotlin source file must start with the following header:

```java
/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */
```

The header is checked by Checkstyle and does not contain a copyright year.

##### Import Statements

The import statements are structured as follows:

* import `java.*`
* blank line
* import `javax.*`
* import `jakarta.*`
* blank line
* import all other imports
* blank line
* import `org.springframework.*`
* blank line
* import static all other imports

Static imports should not be used in production code, but they should be used in test code, especially for things like `import static org.assertj.core.api.Assertions.assertThat;`.

Wildcard imports such as `import java.util.*` or `import static org.assertj.core.api.Assertions.*` are forbidden, even in test code.

##### Java Source File Organization

The following governs how the elements of a source file are organized:

1. static fields
1. normal fields
1. constructors
1. (private) methods called from constructors
1. static factory methods
1. JavaBean properties (i.e., getters and setters)
1. method implementations coming from interfaces
1. private or protected templates that get called from method implementations coming from interfaces
1. other methods
1. `equals`, `hashCode`, and `toString`

Note that private or protected methods called from method implementations should be placed immediately below the methods where they're used.
In other words, if there are 3 interface method implementations with 3 private methods (one used from each), then the order of methods should include 1 interface and 1 private method in sequence, not 3 interface and then 3 private methods at the bottom.

Above all, the organization of the code should feel _natural_.

#### Formatting

##### Braces

Braces mostly follow the _Kernighan and Ritchie style_ (a.k.a., "Egyptian brackets") for nonempty blocks and block-like constructs:

* No line break before the opening brace but prefixed by a single space
* Line break after the opening brace
* Line break before the closing brace
* Line break after the closing brace if that brace terminates a statement or the body of a method, constructor, or named class
* Line break before `else`, `catch`, and `finally` statements

Example:

```java
return new MyClass() {
	@Override
	public void method() {
		if (condition()) {
			something();
		}
		else {
			try {
				alternative();
			}
			catch (ProblemException ex) {
				recover();
			}
		}
	}
};
```

##### Line Wrapping

Place separator symbols at the end of the current line rather than at the beginning of the next line when wrapping a lengthy expression.
In this context, the following are considered separator symbols: `,`, `+`, `?`, `:`, `&&`, `||`.
Many times you can learn by looking at how specific situations are handled in other parts of the code.

For Javadoc, we aim to wrap around 80 characters for maximum readability in all kinds of contexts, for example, reading on GitHub, on your phone, etc.

##### Blank Lines

Add two blank lines before the following elements:

* `static {}` block
* Fields
* Constructors
* Inner classes

Add one blank line after a method signature that is multiline, i.e.

```java
@Override
protected Object invoke(FooBarOperationContext context,
		AnotherSuperLongName name) {

	// code here
}
```

##### Lambdas

Always wrap the parameters of a lambda expression in parentheses, for example `(request) -> request.getName()`.

#### Class Declaration

Try as much as possible to put the `implements`, `extends` section of a class declaration on the same line as the class itself.

Order the classes so that the most important comes first.

#### Naming

##### Constant Names

Constant names use `CONSTANT_CASE`: all uppercase letters, with words separated by underscores.

Every constant is a `static final` field, but not all `static final` fields are constants.
Constant case should therefore be chosen only if the field **is really** a constant.

##### Variable Names

Avoid using single characters as variable names.
For instance, prefer `Method method` to `Method m`.

#### Programming Practices

##### File History

* A file should look like it was crafted by a single author, not like a history of changes.
* Don't artificially spread things out that belong together.

##### Ternary Operator

Wrap the ternary operator within parentheses, for example, `return (foo != null ? foo : "default");`.

Also make sure that the _not null_ condition comes first.

##### Null Checks

Use the `org.springframework.util.Assert.notNull` static method to check that a method argument is not `null` and throw an `IllegalArgumentException` otherwise.
Format the exception message so that the name of the parameter comes first with its first character capitalized, followed by "_must not be null_".
For instance:

```java
public void handle(Event event) {
	Assert.notNull(event, "Event must not be null");
	//...
}
```

For other use cases, use the `org.springframework.util.Assert.state` static method to ensure a variable is not `null` and throw an `IllegalStateException` otherwise.
Format the exception message so that the identifier related to the variable (name, type or description) comes first with its first character capitalized, followed by "_must not be null_".

##### Null Safety of APIs and Fields

The null-safety of Spring for GraphQL APIs and fields must be specified using [JSpecify](https://jspecify.dev/) annotations, and consistency is enforced at build time via [NullAway](https://github.com/uber/NullAway).
For details, see the [Null-safety](https://docs.spring.io/spring-framework/reference/core/null-safety.html) chapter of the Spring Framework reference manual.

Packages are expected to define non-null as the default in `package-info.java`:

```java
@NullMarked
package org.springframework.graphql.mypackage;

import org.jspecify.annotations.NullMarked;
```

Nullable fields, return values, and parameters are expected to be specified explicitly using `@Nullable`:

```java
import org.jspecify.annotations.Nullable;

public class MyClass {

	private @Nullable Class<?> nullableType;

	public void setNullableType(@Nullable Class<?> nullableType) {
		this.nullableType = nullableType;
	}

	public @Nullable Class<?> getNullableType() {
		return this.nullableType;
	}
}
```

Related guidelines:

* When overriding a method, null-safety annotations of the super method need to be specified on the overridden method unless you want to override null-safety.
* Use `@SuppressWarnings("NullAway")` when NullAway triggers irrelevant errors, which can happen due to [NullAway bugs or missing features](https://github.com/uber/NullAway/issues) or when the analysis is not able to prove null-safety (for example, with lambdas).
* Only use the `@Nullable` and `@NonNull` annotations from the `org.jspecify.annotations` package; Java sources must not use Kotlin/JetBrains annotations.

##### Use of @Override

Always add `@Override` on methods overriding or implementing a method declared in a super type.

##### Use of @since

* `@since` should be added to every new class with the version of Spring for GraphQL in which it was introduced.
* `@since` should be added to any *new* **public** and **protected** methods of an existing class.
* `@since` always uses the full `Major.Minor.Patch` form (for example, `@since 2.1.0`, even for a `.0` release); this is enforced by Checkstyle.

##### Utility Classes

A class that is only a collection of static utility methods must be named with a `Utils` suffix, must have a `private` default constructor, and must be `abstract` or `final`.
Providing a `private` _default_ constructor prevents anyone from instantiating it.
For example:

```java
final class MyUtils {

	private MyUtils() {
		/* prevent instantiation */
	}

	// static utility methods
}
```

##### Field and Method References

A field of a class should *always* be referenced using `this`.
A method of a class, however, should never be referenced using `this`.

##### Local Variable Type Inference

The use of `var` for variable declarations (_local variable type inference_) is not permitted in main sources.
Instead, declare variables using the concrete type or interface (where applicable).

##### Logging and Console Output

Use Apache Commons Logging (`org.apache.commons.logging.LogFactory`) for logging; using SLF4J's `LoggerFactory` directly is forbidden by the architecture checks.
Printing to `System.out` or `System.err`, and calling `printStackTrace()`, are forbidden in the main codebase.

##### Architecture Rules

The following rules are enforced by the `checkArchitectureMain` task:

* Packages must be free of cyclic dependencies.
* `String.toLowerCase()` and `String.toUpperCase()` must be called with a `Locale`, usually `Locale.ROOT`.
* Every `package-info.java` must be annotated with `@NullMarked`.

#### Javadoc

##### Javadoc Formatting

The following template summarizes typical Javadoc usage for a method.

```java
/**
 * Add a registrar to get access to and configure the
 * {@link DataLoaderRegistry} for each request.
 * <p>Registrars are invoked in the order in which they were added. This
 * method must not be called after the first request was executed.
 * @param registrar the registrar to add; may be {@code null} to ignore the call
 * @return the current instance, for chained calls
 * @since 2.1.0
 */
```

In particular, please note:

* Use an imperative style (i.e. _Return_ and not _Returns_) for the first sentence.
* No blank lines between the description and the parameter descriptions.
* If the description is defined with multiple paragraphs, start each of them with `<p>`.
* If a parameter description needs to be wrapped, do not indent subsequent lines.

The Javadoc of a class has some extra rules that are illustrated by the sample below:

```java
/**
 * {@link ExecutionGraphQlService} that uses a {@link GraphQlSource} to obtain a
 * {@link GraphQL} instance and perform query execution.
 *
 * <p>Implementations are free to customize the execution input.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see GraphQlSource
 */
```

* Each class must have a `@since` tag with the version in which the class was introduced.
* The order of tags for type-level Javadoc is: `@author`, `@since`, `@param`, `@see`, `@deprecated`.
* The order of tags for constructor-level, method-level, and field-level Javadoc is: `@param`, `@return`, `@throws`, `@since`, `@see`, `@deprecated`.
* In contrast to constructor-level, method-level, and field-level Javadoc, the paragraphs of a class description *are* separated by blank lines.

The following are additional general rules to apply when writing Javadoc:

* Use `{@code}` to wrap code statements or values such as `null`.
* If a type is only referenced by a `{@link}` element, use the fully qualified name in order to avoid an unnecessary `import` declaration.

### Tests

Any code change should come with new or updated tests.

* Tests must be written using JUnit Jupiter (Kotlin tests may use [MockK](https://mockk.io/)).
* Each test class name must end with a `Tests` suffix. Test classes and methods are package-private.
* Use [AssertJ](https://assertj.github.io/doc/) for assertions and assumptions, including specialized assertions such as `assertThatIllegalArgumentException()` instead of `assertThatExceptionOfType(IllegalArgumentException.class)`.
* Use [Mockito](https://site.mockito.org/) for mocks and spies.
Use the standard Mockito API (for example, `when(...).thenReturn(...)`) instead of `BDDMockito` (for example, `given(...).willReturn(...)`).
Existing usage of `BDDMockito` does not need to be migrated.

### Reference Docs

The reference documentation is authored in [AsciiDoc](https://asciidoc.org/) format using [Antora](https://docs.antora.org/antora/latest/).
The source files for the documentation reside in the [spring-graphql-docs/modules/ROOT](spring-graphql-docs/modules/ROOT) directory.
Java code snippets included via `include-code::` reside in [spring-graphql-docs/src/main](spring-graphql-docs/src/main) and are compiled as part of the build.
For trivial changes, you may be able to browse, edit source files, and submit directly from GitHub.

When making changes locally, execute `./gradlew :spring-graphql-docs:antora` and then browse the results under `spring-graphql-docs/build/site`.
The aggregated Javadoc can be generated with `./gradlew :spring-graphql-docs:api`.

Asciidoctor also supports live editing.
For more details see [AsciiDoc Tooling](https://docs.asciidoctor.org/asciidoctor/latest/tooling/).

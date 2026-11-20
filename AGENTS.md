# Spring for GraphQL agent guidelines

Concise extract of [CONTRIBUTING.md](CONTRIBUTING.md), which remains the reference for humans.

## Build

- Gradle build, JDK from `.sdkmanrc` (currently JDK 25, `sdk env install` / `sdk env`); artifacts target Java 17+, with a Java 21 multi-release source set in `spring-graphql`.
- Modules: `spring-graphql`, `spring-graphql-test`, `spring-graphql-docs`, `platform`.
- Scope builds to the modules you touched: `./gradlew :spring-graphql:test`, a single test with `--tests <fully.qualified.ClassTests>`.
- Run `./gradlew :<module>:check` before finishing: it runs tests, Checkstyle (`src/checkstyle/checkstyle.xml`, Spring Java Format rules) and the ArchUnit checks (`checkArchitectureMain`), which enforce most of the code style below.
- Main sources compile with `-Werror`; nullability checks (NullAway) run during compilation. Fix reported errors instead of suppressing them.
- Some tests use Testcontainers and need a container runtime.
- Do not run `clean` unless needed; the build is incremental and cached.
- Reference docs: `./gradlew :spring-graphql-docs:antora`, output in `spring-graphql-docs/build/site`.

## Code style

- Match surrounding code; do not reformat unrelated code; a file should look like it was written by a single author.
- Tabs, LF, UTF-8, no trailing whitespace.
- Wrap lines after separators (`,` `+` `?` `:` `&&` `||`), never before; wrap Javadoc around 80 characters.
- K&R braces, with `else`, `catch`, and `finally` on a new line.
- Two blank lines before fields, constructors, `static {}` blocks, and inner classes; one blank line after a multiline method signature.
- Lambda parameters always in parentheses: `(request) -> ...`.
- Import order, groups separated by a blank line: `java.*`, then `javax.*` + `jakarta.*`, then others, then `org.springframework.*`, then static imports.
- No wildcard imports. No static imports in production code; use them in tests (e.g. `assertThat`).
- Every Java and Kotlin source file, including `package-info.java`: the dateless license header pointing to `NOTICE` and `LICENSE` (copy from an existing file or `src/checkstyle/checkstyle-header.txt`), package, imports, exactly one top-level class.
- Always reference fields with `this.`, never methods. Always add `@Override`.
- No `var` in production code. No single-character variable names. Wrap ternaries in parentheses with the non-null condition first: `(foo != null ? foo : "default")`.
- Argument checks: `Assert.notNull(event, "Event must not be null")`; state checks: `Assert.state(...)` (`org.springframework.util.Assert`).
- Null-safety with JSpecify: packages are `@NullMarked` in `package-info.java`, use `org.jspecify.annotations.Nullable` explicitly (e.g. `private @Nullable String name;`), repeat super method nullness on overrides. Java code must not use Kotlin/JetBrains annotations.
- Logging with `org.apache.commons.logging.LogFactory`, never SLF4J `LoggerFactory`. No `System.out`/`System.err` or `printStackTrace()`.
- `toLowerCase(Locale.ROOT)` / `toUpperCase(Locale.ROOT)`, never the no-argument variants. No package cycles.
- Static utility classes: `Utils` suffix, private constructor.

## Javadoc

- First sentence in imperative style ("Return", not "Returns"); `<p>` to start extra paragraphs; `{@code}` for code and `null`.
- No blank line between method description and tags; do not indent wrapped tag descriptions.
- Add `@since` to new classes and new public/protected methods, always in full `Major.Minor.Patch` form (`@since 2.1.0`, not `2.1`); Checkstyle enforces it.
- Tag order for types: `@author`, `@since`, `@param`, `@see`, `@deprecated`; for members: `@param`, `@return`, `@throws`, `@since`, `@see`, `@deprecated`.

## Tests

- Add or update tests for any code change.
- JUnit Jupiter, AssertJ (including `assertThatIllegalArgumentException()` and similar), Mockito (MockK for Kotlin tests). No JUnit 4/Jupiter/TestNG assertions, no Hamcrest.
- Test class names end with `Tests`; test classes and methods are package-private.
- Use the standard Mockito API (`when(...).thenReturn(...)`), not `BDDMockito` (`given(...)`), in new test code; do not migrate existing `BDDMockito` usage.

## Docs

- Reference docs are AsciiDoc in `spring-graphql-docs/modules/ROOT`; code snippets for `include-code::` live in `spring-graphql-docs/src/main/java`.

## Commits and pull requests

- Pull requests target `main` (see `gradle.properties` for the version); backports to maintenance branches are handled by the maintainers.
- Subject: imperative, capitalized verb, concise (about 60 characters at most), no `fix:`/`docs:` prefixes, no issue/PR number, no trailing period.
- Body wrapped at 72 characters explaining the motivation, followed by `Closes gh-123` (or `See gh-123`), then a `Signed-off-by: Name <email>` trailer (DCO, use `git commit -s`).
- Never write annotations verbatim in commit messages or PR titles (it mentions GitHub users): enclose them in backticks, as in `` `@Override` ``.
- Changes must be reviewed by a human who remains accountable for them before being submitted.

Sample with Spring Security in a WebFlux application.

Main features:

 - Spring Security [config](src/main/java/io/spring/sample/graphql/SecurityConfig.java) secures GraphQL HTTP endpoint.
 - Fine-grained, method-level security on [SalaryService](src/main/java/io/spring/sample/graphql/SalaryService.java).
 - `AuthenticationException` and `AccessDeniedException` [resolved](src/main/java/io/spring/sample/graphql/SecurityDataFetcherExceptionResolver.java) to GraphQL errors.
 - [Tests](src/test/java/io/spring/sample/graphql/SampleApplicationTests.java) with `WebGraphQlTester` and WebFlux without a server.
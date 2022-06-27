**Spring Security for GraphQL HTTP Endpoint with WebFlux**

 - Spring Security [config](src/main/java/io/spring/sample/graphql/SecurityConfig.java) secures GraphQL HTTP endpoint.
 - Fine-grained, method-level security on [SalaryService](src/main/java/io/spring/sample/graphql/SalaryService.java).
 - `AuthenticationException` and `AccessDeniedException` resolved to GraphQL errors.
 - [Tests](src/test/java/io/spring/sample/graphql/WebFluxSecuritySampleTests.java) with `WebGraphQlTester` and WebFlux without a server.
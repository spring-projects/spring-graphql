**GraphQL HTTP Endpoint with Spring MVC**

 - Spring HATEOAS [DataFetcher's](src/main/java/io/spring/sample/graphql/project/ProjectDataWiring.java) that call spring.io REST API.
 - Querydsl [DataFetcher's](src/main/java/io/spring/sample/graphql/repository/ArtifactRepositoryDataWiring.java) making JPA queries.
 - Use of [ThreadLocalAccessor](src/main/java/io/spring/sample/graphql/greeting/RequestAttributesAccessor.java) to propagate context to data fetchers.
 - Schema printing enabled at "/graphql/schema".
 - [Tests](src/test/java/io/spring/sample/graphql/project/MockMvcGraphQlTests.java) with `GraphQlTester` and MockMvc. 
**GraphQL HTTP Endpoint with Spring MVC**

 - [Data Controller](src/main/java/io/spring/sample/graphql/project/ProjectController.java) with Spring HATEOAS calls to spring.io.
 - Querydsl [GraphQlRepository](src/main/java/io/spring/sample/graphql/repository/ArtifactRepositories.java) making JPA queries.
 - Use of [ThreadLocalAccessor](src/main/java/io/spring/sample/graphql/greeting/RequestAttributesAccessor.java) to propagate context to data fetchers.
 - Schema printing enabled at "/graphql/schema".
 - [Tests](src/test/java/io/spring/sample/graphql/project/MockMvcGraphQlTests.java) with `GraphQlTester` and MockMvc. 
# Spring GraphQL

Experimental project for [GraphQL](https://graphql.org/) support in Spring applications with [GraphQL Java](https://github.com/graphql-java/graphql-java).

[![Build status](https://ci.spring.io/api/v1/teams/spring-graphql/pipelines/spring-graphql/jobs/build/badge)](https://ci.spring.io/teams/spring-graphql/pipelines/spring-graphql)


## Getting started

This project is tested against Spring Boot 2.4+, but should work on 2.3 as well.

You can start by creating a project on https://start.spring.io and select the `spring-boot-starter-web` or `spring-boot-starter-webflux` starter,
depending on the type of web application you'd like to build. Once the project is generated, you can manually add the
`org.springframework.experimental:graphql-spring-boot-starter` dependency.

`build.gradle` snippet:
```groovy
dependencies {
    implementation 'org.springframework.experimental:graphql-spring-boot-starter:1.0.0-SNAPSHOT'
    
    // Spring Web MVC starter
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // OR Spring WebFlux starter
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

} 

repositories {
    mavenCentral()
    // don't forget to add spring milestone or snapshot repositories
    maven { url 'https://repo.spring.io/milestone' }
    maven { url 'https://repo.spring.io/snapshot' }
}
``` 

`pom.xml` snippet:
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.experimental</groupId>
        <artifactId>graphql-spring-boot-starter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Spring Web MVC starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- OR Spring WebFlux starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <!-- ... -->
</dependencies>

<!-- Don't forget to add spring milestone or snapshot repositories -->
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
    </repository>
    <repository>
        <id>spring-snapshots</id>
        <name>Spring Snapshots</name>
        <url>https://repo.spring.io/snapshot</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

You can now add a GraphQL schema in `src/main/resources/schema.graphqls` such as:

```
type Query {
    people: [Person]!
}

type Person {
    id: ID!
    name: String!
}
```

Then you should configure the data fetching process using a `RuntimeWiringCustomizer` and custom components like
Spring Data repositories, `WebClient` instances for Web APIs, a `@Service` bean, etc. 

```java
@Component
public class PersonDataWiring implements RuntimeWiringCustomizer {

	private final PersonService personService;

	public PersonDataWiring(PersonService personService) {
		this.personService = personService;
	}

	@Override
	public void customize(RuntimeWiring.Builder builder) {
		builder.type("Query", typeWiring -> typeWiring
				.dataFetcher("people", env -> this.personService.findAll()));
	}
}
```

You can now start your application!
A GraphiQL web interface is available at `http://localhost:8080/graphql` and you can use GraphQL clients
to POST queries at the same location.


## Features

### Core configuration
The Spring GraphQL project offers a few configuration properties to customize your application: 

````properties
# web path to the graphql endpoint
spring.graphql.path=/graphql
# location of the graphql schema file
spring.graphql.schema.location=classpath:graphql/schema.graphqls
# schema printer endpoint configuration
# endpoint path is concatenated with the main path, so "/graphql/schema" by default
spring.graphql.schema.printer.enabled=false
spring.graphql.schema.printer.path=/schema
# whether micrometer metrics should be collected for graphql queries
management.metrics.graphql.autotime.enabled=true
````

You can contribute `RuntimeWiringCustomizer` beans to the context in order to configure the runtime wiring of your GraphQL application.

### WebSocket support

This project also supports WebSocket as a transport for GraphQL requests - you can use it to build [`Subscription` queries](http://spec.graphql.org/draft/#sec-Subscription).
This use case is powered by Reactor `Flux`, check out the `samples/webflux-websocket` sample application for more.

To enable this support, you need to configure the `spring.graphql.websocket.path` property in your application
and have the required dependencies on classpath. In the case of a Servlet application, adding the `spring-boot-starter-websocket` should be enough. 

WebSocket support comes with dedicated properties:

````properties
# Path of the GraphQL WebSocket subscription endpoint.
spring.graphql.websocket.path=/graphql/websocket
# Time within which the initial {@code CONNECTION_INIT} type message must be received.
spring.graphql.websocket.connection-init-timeout=60s
````

### Extension points

You can contribute [`WebInterceptor` beans](https://github.com/spring-projects-experimental/spring-graphql/blob/master/spring-graphql/src/main/java/org/springframework/graphql/WebInterceptor.java)
to the application context, so as to customize the `ExecutionInput` or the `ExecutionResult` of the query.
A custom `WebInterceptor` can, for example, change the HTTP request/response headers.  

### Testing support

When the `spring-boot-starter-test` dependency is on the classpath, Spring GraphQL provides a testing infrastructure for your application.
                                                                                                                                          
Spring Boot allows you to test your web application with [with a mock environment](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.spring-boot-applications.with-mock-environment)
or [with a running server](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing.spring-boot-applications.with-running-server).
In both cases, adding the `@AutoConfigureGraphQlTester` annotation on your test class will contribute a `GraphQlTester` bean you can inject and use in your tests:

```` java
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureGraphQlTester
public class MockMvcGraphQlTests {

	@Autowired
	private GraphQlTester graphQlTester;

	@Test
	void jsonPath() {
		String query = "{" +
				"  project(slug:\"spring-framework\") {" +
				"    releases {" +
				"      version" +
				"    }" +
				"  }" +
				"}";

		this.graphQlTester.query(query)
				.execute()
				.path("project.releases[*].version")
				.entityList(String.class)
				.hasSizeGreaterThan(1);
	}
}
````

### Metrics

If the `spring-boot-starter-actuator` dependency is on the classpath, metrics will be collected for GraphQL requests.
You can see those metrics by exposing the metrics endpoint with `application.properties`:
```properties
management.endpoints.web.exposure.include=health,metrics,info
```
                                                                                                   
#### GraphQL Request (timer)

A Request metric timer is available at `/actuator/metrics/graphql.request`.

| Tag     | Description     | Sample values      |
|---------|-----------------|--------------------|
| outcome | Request outcome | "SUCCESS", "ERROR" |


#### GraphQL Data Fetcher (timer)

A Data Fetcher metric timer is available at `/actuator/metrics/graphql.datafetcher`.

| Tag     | Description           | Sample values      |
|---------|-----------------------|--------------------|
| path    | data fetcher path     | "Query.project"    |
| outcome | data fetching outcome | "SUCCESS", "ERROR" |


#### GraphQL Error (counter)

A counter metric counter is available at `/actuator/metrics/graphql.error`.

| Tag       | Description     | Sample values           |
|-----------|-----------------|-------------------------|
| errorType | error type      | "DataFetchingException" |
| errorPath | error JSON Path | "$.project"             |                                

## Sample applications

This repository contains sample applications that the team is using to test new features and ideas.

You can run them by cloning this repository and typing on the command line:

```shell script
$ ./gradlew :samples:webmvc-http:bootRun
$ ./gradlew :samples:webflux-websocket:bootRun
```


## License

This project is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).

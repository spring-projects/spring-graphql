# Spring GraphQL

Experimental project to create [GraphQL](https://graphql.org/) support for Spring applications.

[![Build status](https://ci.spring.io/api/v1/teams/spring-graphql/pipelines/spring-graphql/jobs/build/badge)](https://ci.spring.io/teams/spring-graphql/pipelines/spring-graphql)


## Getting started

This project is tested against Spring Boot 2.4+, but should work on 2.3 as well.

You can start by creating a project on https://start.spring.io and select the `spring-boot-starter-web` or `spring-boot-starter-webflux` starter,
depending on the type of web application you'd like to build. Once the project is generated, you can manually add the
`org.springframework.experimental:graphql-spring-boot-starter` dependency.

`build.gradle` snippet:
```groovy
dependencies {
    implementation 'org.springframework.experimental:graphql-spring-boot-starter:0.1.0-SNAPSHOT'
    
    // Spring Web MVC starter
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // OR Spring WebFlux starter
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

} 

repositories {
    mavenCentral()
    // don't forget to add spring milestone and snapshot repositories
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
        <version>0.1.0-SNAPSHOT</version>
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

And then you should configure the data fetching using a `RuntimeWiringCustomizer` and custom components like
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
spring.graphql.schema-location=classpath:/schema.graphqls
# Whether micrometer metrics should be collected for graphql queries
management.metrics.graphql.autotime.enabled=true
````

You can contribute `RuntimeWiringCustomizer` beans to the context in order to configure the runtime wiring of your GraphQL application.

### Metrics

If the `spring-boot-starter-actuator` dependency is on the classpath, metrics will be collected for GraphQL queries.
You can see those metrics by exposing the metrics endpoint with `application.properties`:
```properties
management.endpoints.web.exposure.include=health,metrics,info
```

You can then check those metrics at `http://localhost:8080/actuator/metrics/graphql.query`.


## Sample applications

This repository contains sample applications that the team is using to test new features and ideas.

You can run them by cloning this repository and typing on the command line:

```shell script
$ ./gradlew :samples:webmvc-http:bootRun
$ ./gradlew :samples:webflux-websocket:bootRun
```


## License

This project is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).

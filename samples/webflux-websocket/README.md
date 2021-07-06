**GraphQL WebSocket Endpoint with Spring WebFlux**

 - Reactive [DataFetcher's](src/main/java/io/spring/sample/graphql/SampleWiring.java), for queries and subscriptions.
 - [WebFilter](src/main/java/io/spring/sample/graphql/ContextWebFilter.java) to insert Reactor `Context` and check it can be accessed in [DataRepository](src/main/java/io/spring/sample/graphql/DataRepository.java).
 - Subscription [tests](src/test/java/io/spring/sample/graphql/SubscriptionTests.java) using Reactor's `StepVerifier` to verify the stream. 
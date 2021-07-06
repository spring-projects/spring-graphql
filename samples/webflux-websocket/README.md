Sample with GraphQL over WebSocket in a WebFlux application.

Main features:

 - Reactive [DataFetcher's](src/main/java/io/spring/sample/graphql/SampleWiring.java), including subscription stream.
 - [WebFilter](src/main/java/io/spring/sample/graphql/ContextWebFilter.java) that inserts Reactor `Context` that is then accessed in the [DataRepository](src/main/java/io/spring/sample/graphql/DataRepository.java).
 - [Tests](src/test/java/io/spring/sample/graphql/SubscriptionTests.java) for subscription streams. 
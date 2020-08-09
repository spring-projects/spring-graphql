package org.springframework.graphql.reactive.components;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.reactive.GraphQLInvocationData;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class GraphQLController {

    @Autowired
    GraphQLRequestHandler graphQLRequestHandler;

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {
        RouterFunction<ServerResponse> route = route()
                .POST("/graphql", this::graphqlPOST)
                .build();
        return route;

    }

    private Mono<ServerResponse> graphqlPOST(ServerRequest serverRequest) {
        Mono<GraphQLRequestBody> bodyMono = serverRequest.bodyToMono(GraphQLRequestBody.class);
        return bodyMono.flatMap(body -> {
            String query = body.getQuery();
            if (query == null) {
                query = "";
            }
            GraphQLInvocationData invocationData = new GraphQLInvocationData(query, body.getOperationName(), body.getVariables());
            Mono<Map> resultBodyMono = graphQLRequestHandler.invoke(invocationData, serverRequest.headers());
            return resultBodyMono.flatMap(resultBody -> ServerResponse.ok().bodyValue(resultBody));
        });
    }

}

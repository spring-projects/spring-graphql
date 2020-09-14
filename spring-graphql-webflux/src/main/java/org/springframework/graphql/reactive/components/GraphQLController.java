package org.springframework.graphql.reactive.components;


import graphql.GraphQL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.DefaultGraphQLInterceptor;
import org.springframework.graphql.GraphQLHandler;
import org.springframework.graphql.GraphQLHttpRequest;
import org.springframework.graphql.GraphQLInterceptor;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class GraphQLController {

    @Autowired
    GraphQL graphQL;

    GraphQLHandler graphQLHandler;

    @Autowired(required = false)
    GraphQLInterceptor graphQLInterceptor;

    @PostConstruct
    public void init() {
        GraphQLInterceptor interceptor = graphQLInterceptor == null ? new DefaultGraphQLInterceptor() : graphQLInterceptor;
        this.graphQLHandler = new GraphQLHandler(graphQL, interceptor);
    }

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {
        RouterFunction<ServerResponse> route = route()
                .POST("/graphql", this::graphqlPOST)
                .build();
        return route;

    }

    private Mono<ServerResponse> graphqlPOST(ServerRequest serverRequest) {
        Mono<GraphQLReactiveRequestBody> bodyMono = serverRequest.bodyToMono(GraphQLReactiveRequestBody.class);
        return bodyMono.flatMap(body -> {
            String query = body.getQuery();
            if (query == null) {
                query = "";
            }
            Map<String, Object> variables = body.getVariables();
            if (variables == null) {
                variables = Collections.emptyMap();
            }
            GraphQLHttpRequest graphQLHttpRequest = new GraphQLHttpRequest(
                    query,
                    body.getOperationName(),
                    variables,
                    serverRequest.headers().asHttpHeaders(),
                    serverRequest.queryParams());
            return graphQLHandler.graphqlPOST(graphQLHttpRequest);
        }).flatMap(graphQLHttpResponse -> {
            //TODO: this should be handled better:
            // we don't want to serialize `null` values for `errors` and `extensions`
            // this is why we convert it to a Map here
            Map<String, Object> responseBodyRaw = new LinkedHashMap<>();
            responseBodyRaw.put("data", graphQLHttpResponse.getData());
            if (graphQLHttpResponse.getErrors() != null) {
                responseBodyRaw.put("errors", graphQLHttpResponse.getErrors());
            }
            if (graphQLHttpResponse.getExtensions() != null) {
                responseBodyRaw.put("extensions", graphQLHttpResponse.getExtensions());
            }
            return ServerResponse.ok().headers(httpHeaders -> {
                httpHeaders.addAll(graphQLHttpResponse.getHttpHeaders());
            }).bodyValue(responseBodyRaw);
        });
    }

}

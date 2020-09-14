package org.springframework.graphql.servlet.components;


import graphql.GraphQL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.DefaultGraphQLInterceptor;
import org.springframework.graphql.GraphQLHandler;
import org.springframework.graphql.GraphQLHttpRequest;
import org.springframework.graphql.GraphQLInterceptor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.web.servlet.function.RouterFunctions.route;

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
                .POST("/graphql", RequestPredicates.contentType(MediaType.APPLICATION_JSON)
                        .or(RequestPredicates.contentType(MediaType.APPLICATION_JSON_UTF8)), this::graphqlPOST)
                .build();
        return route;

    }

    private ServerResponse graphqlPOST(ServerRequest serverRequest) {
        GraphQLServletRequestBody body = null;
        try {
            body = serverRequest.body(GraphQLServletRequestBody.class);
        } catch (ServletException | IOException e) {
            e.printStackTrace();
        }
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
                serverRequest.params());

        Mono<Map<String, Object>> responseRawMono = graphQLHandler.graphqlPOST(graphQLHttpRequest)
                .map(graphQLResponseBody -> {
                    //TODO: this should be handled better:
                    // we don't want to serialize `null` values for `errors` and `extensions`
                    // this is why we convert it to a Map here
                    Map<String, Object> responseBodyRaw = new LinkedHashMap<>();
                    responseBodyRaw.put("data", graphQLResponseBody.getData());
                    if (graphQLResponseBody.getErrors() != null) {
                        responseBodyRaw.put("errors", graphQLResponseBody.getErrors());
                    }
                    if (graphQLResponseBody.getExtensions() != null) {
                        responseBodyRaw.put("extensions", graphQLResponseBody.getExtensions());
                    }
                    return responseBodyRaw;
                });
        return ServerResponse.ok().body(responseRawMono);
    }

}

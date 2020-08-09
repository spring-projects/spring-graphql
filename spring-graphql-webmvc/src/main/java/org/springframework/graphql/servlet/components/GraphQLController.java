package org.springframework.graphql.servlet.components;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.servlet.GraphQLInvocationData;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration
public class GraphQLController {

    @Autowired
    GraphQLRequestHandler graphQLRequestHandler;

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {
        RouterFunction<ServerResponse> route = route()
                .POST("/graphql", RequestPredicates.contentType(MediaType.APPLICATION_JSON)
                        .or(RequestPredicates.contentType(MediaType.APPLICATION_JSON_UTF8)), this::graphqlPOST)
                .build();
        return route;

    }

    private ServerResponse graphqlPOST(ServerRequest serverRequest) {
        GraphQLRequestBody body = null;
        try {
            body = serverRequest.body(GraphQLRequestBody.class);
        } catch (ServletException | IOException e) {
            e.printStackTrace();
        }
        String query = body.getQuery();
        if (query == null) {
            query = "";
        }
        GraphQLInvocationData invocationData = new GraphQLInvocationData(query, body.getOperationName(), body.getVariables());
        Object resultBody = graphQLRequestHandler.invoke(invocationData, serverRequest.headers());
        return ServerResponse.ok().body(resultBody);
    }

}

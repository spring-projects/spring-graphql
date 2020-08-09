package org.springframework.graphql.components;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.GraphQLInvocationData;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;

import static org.springframework.web.servlet.function.RouterFunctions.route;

@Component
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

    private ServerResponse graphqlPOST(ServerRequest serverRequest) {
        GraphQLRequestBody body = null;
        try {
            body = serverRequest.body(GraphQLRequestBody.class);
        } catch (javax.servlet.ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
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

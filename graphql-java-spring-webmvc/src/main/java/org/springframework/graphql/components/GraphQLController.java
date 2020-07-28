package org.springframework.graphql.components;


import graphql.ExecutionResult;
import graphql.Internal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.ExecutionResultHandler;
import org.springframework.graphql.GraphQLInvocation;
import org.springframework.graphql.GraphQLInvocationData;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.concurrent.CompletableFuture;

@RestController
@Internal
public class GraphQLController {

    @Autowired
    GraphQLInvocation graphQLInvocation;

    @Autowired
    ExecutionResultHandler executionResultHandler;

    @RequestMapping(value = "${graphql.url:graphql}",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Object graphqlPOST(@RequestBody GraphQLRequestBody body,
                              WebRequest webRequest) {
        String query = body.getQuery();
        if (query == null) {
            query = "";
        }
        CompletableFuture<ExecutionResult> executionResult = graphQLInvocation.invoke(new GraphQLInvocationData(query, body.getOperationName(), body.getVariables()), webRequest);
        return executionResultHandler.handleExecutionResult(executionResult);
    }

}

package org.springframework.graphql.components;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Internal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.ExecutionInputCustomizer;
import org.springframework.graphql.GraphQLInvocation;
import org.springframework.graphql.GraphQLInvocationData;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.concurrent.CompletableFuture;

@Component
@Internal
public class DefaultGraphQLInvocation implements GraphQLInvocation {

    @Autowired
    GraphQL graphQL;

    @Autowired
    ExecutionInputCustomizer executionInputCustomizer;

    @Override
    public CompletableFuture<ExecutionResult> invoke(GraphQLInvocationData invocationData, WebRequest webRequest) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(invocationData.getQuery())
                .operationName(invocationData.getOperationName())
                .variables(invocationData.getVariables())
                .build();
        CompletableFuture<ExecutionInput> customizedExecutionInput = executionInputCustomizer.customizeExecutionInput(executionInput, webRequest);
        return customizedExecutionInput.thenCompose(graphQL::executeAsync);
    }

}

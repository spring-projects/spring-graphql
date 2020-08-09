package org.springframework.graphql.components;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.GraphQLInvocationData;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class GraphQLRequestHandler {

    @Autowired
    private GraphQL graphQL;


    public Object invoke(GraphQLInvocationData invocationData,
                         ServerRequest.Headers headers) {
        Assert.notNull(graphQL, "graphQL is not set");
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(invocationData.getQuery())
                .operationName(invocationData.getOperationName())
                .variables(invocationData.getVariables())
                .build();
        customizeExecutionInput(executionInput, headers);
        CompletableFuture<ExecutionInput> customizedExecutionInput = customizeExecutionInput(executionInput, headers);
        CompletableFuture<ExecutionResult> executionResultCompletableFuture = customizedExecutionInput.thenCompose(graphQL::executeAsync);
        return handleExecutionResult(executionResultCompletableFuture);
    }

    protected CompletableFuture<ExecutionInput> customizeExecutionInput(ExecutionInput executionInput,
                                                                        ServerRequest.Headers headers) {
        return CompletableFuture.completedFuture(executionInput);
    }

    protected Object handleExecutionResult(CompletableFuture<ExecutionResult> executionResultCF) {
        if (executionResultCF.isDone()) {
            return toSpecification(executionResultCF);
        }
        return executionResultCF.thenApply(ExecutionResult::toSpecification);
    }

    private Map<String, Object> toSpecification(CompletableFuture<ExecutionResult> executionResultCF) {
        try {
            return executionResultCF.get().toSpecification();
        } catch (Exception e) {
            throw new RuntimeException("Should not happen", e);
        }
    }
}

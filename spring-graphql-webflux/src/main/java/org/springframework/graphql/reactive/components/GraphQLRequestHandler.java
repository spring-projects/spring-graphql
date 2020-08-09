package org.springframework.graphql.reactive.components;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.reactive.GraphQLInvocationData;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class GraphQLRequestHandler {

    @Autowired
    private GraphQL graphQL;

    public Mono<Map> invoke(GraphQLInvocationData invocationData,
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

    protected Mono<Map> handleExecutionResult(CompletableFuture<ExecutionResult> executionResultCF) {
        return Mono.fromCompletionStage(executionResultCF).map(ExecutionResult::toSpecification);
    }
}

package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class GraphQLHandler {
    private GraphQL graphQL;

    private GraphQLInterceptor interceptor;

    public GraphQLHandler(GraphQL graphQL, GraphQLInterceptor interceptor) {
        this.graphQL = graphQL;
        this.interceptor = interceptor;
    }

    public Mono<GraphQLResponse> graphqlPOST(GraphQLRequestBody body, HttpHeaders httpHeaders) {
        String query = body.getQuery();
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(body.getOperationName())
                .variables(body.getVariables())
                .build();
        Mono<ExecutionInput> executionInput = interceptor.preHandle(input, httpHeaders);
        return executionInput
                .flatMap(this::execute)
                .flatMap(result -> interceptor.postHandle(result, httpHeaders))
                .flatMap(result -> toResponseBody(result, httpHeaders));
    }

    private Mono<GraphQLResponse> toResponseBody(ExecutionResult executionResult, HttpHeaders httpHeaders) {
        Map<String, Object> responseBodyRaw = executionResult.toSpecification();
        Object data = responseBodyRaw.get("data");
        List<Map<String, Object>> errors = (List<Map<String, Object>>) responseBodyRaw.get("errors");
        Map<String, Object> extensions = (Map<String, Object>) responseBodyRaw.get("extensions");
        GraphQLResponse responseBody = new GraphQLResponse(data,
                errors,
                extensions);
        Mono<GraphQLResponse> graphQLResponseBodyMono = interceptor.customizeResponseBody(responseBody, executionResult, httpHeaders);
        return graphQLResponseBodyMono;
    }


    protected Mono<ExecutionResult> execute(ExecutionInput input) {
        return Mono.fromCompletionStage(graphQL.executeAsync(input));
    }
}

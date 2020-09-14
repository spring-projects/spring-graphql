package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
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

    public Mono<GraphQLHttpResponse> graphqlPOST(GraphQLHttpRequest graphQLHttpRequest) {
        String query = graphQLHttpRequest.getQuery();
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(graphQLHttpRequest.getOperationName())
                .variables(graphQLHttpRequest.getVariables())
                .build();
        MultiValueMap<String, String> requestParams = graphQLHttpRequest.getRequestParams();
        Mono<ExecutionInput> executionInput = interceptor.preHandle(input,
                graphQLHttpRequest.getHttpHeaders(),
                requestParams);
        return executionInput
                .flatMap(this::execute)
                .flatMap(result -> interceptor.postHandle(result, graphQLHttpRequest.getHttpHeaders(), requestParams))
                .flatMap(result -> toResponseBody(result, graphQLHttpRequest));
    }

    private Mono<GraphQLHttpResponse> toResponseBody(ExecutionResult executionResult, GraphQLHttpRequest graphQLHttpRequest) {
        Map<String, Object> responseBodyRaw = executionResult.toSpecification();
        Object data = responseBodyRaw.get("data");
        List<Map<String, Object>> errors = (List<Map<String, Object>>) responseBodyRaw.get("errors");
        Map<String, Object> extensions = (Map<String, Object>) responseBodyRaw.get("extensions");
        GraphQLHttpResponse responseBody = new GraphQLHttpResponse(data,
                errors,
                extensions,
                new HttpHeaders());
        Mono<GraphQLHttpResponse> graphQLResponseBodyMono = interceptor.customizeGraphQLHttpResponse(responseBody, executionResult, graphQLHttpRequest);
        return graphQLResponseBodyMono;
    }


    protected Mono<ExecutionResult> execute(ExecutionInput input) {
        return Mono.fromCompletionStage(graphQL.executeAsync(input));
    }
}

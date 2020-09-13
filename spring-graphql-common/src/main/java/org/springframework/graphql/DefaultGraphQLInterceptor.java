package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

public class DefaultGraphQLInterceptor implements GraphQLInterceptor {

    @Override
    public Mono<ExecutionInput> preHandle(ExecutionInput input, HttpHeaders headers) {
        return Mono.just(input);
    }

    @Override
    public Mono<ExecutionResult> postHandle(ExecutionResult result, HttpHeaders httpHeaders) {
        return Mono.just(result);
    }

    @Override
    public Mono<GraphQLResponseBody> customizeResponseBody(GraphQLResponseBody graphQLResponseBody, ExecutionResult executionResult, HttpHeaders httpHeader) {
        return Mono.just(graphQLResponseBody);
    }
}
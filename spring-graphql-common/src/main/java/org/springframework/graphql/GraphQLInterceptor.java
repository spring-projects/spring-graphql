package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

public interface GraphQLInterceptor {

    default Mono<ExecutionInput> preHandle(ExecutionInput input, HttpHeaders headers) {
        return Mono.just(input);
    }

    default Mono<ExecutionResult> postHandle(ExecutionResult result, HttpHeaders httpHeaders) {
        return Mono.just(result);
    }

    default Mono<GraphQLResponse> customizeResponseBody(GraphQLResponse graphQLResponse, ExecutionResult executionResult, HttpHeaders httpHeader) {
        return Mono.just(graphQLResponse);
    }

}
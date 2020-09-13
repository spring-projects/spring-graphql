package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

public interface GraphQLInterceptor {

    Mono<ExecutionInput> preHandle(ExecutionInput input, HttpHeaders headers);

    Mono<ExecutionResult> postHandle(ExecutionResult result, HttpHeaders httpHeaders);

    Mono<GraphQLResponseBody> customizeResponseBody(GraphQLResponseBody graphQLResponseBody, ExecutionResult executionResult, HttpHeaders httpHeader);

}
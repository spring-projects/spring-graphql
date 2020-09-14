package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public interface GraphQLInterceptor {

    default Mono<ExecutionInput> preHandle(ExecutionInput input,
                                           HttpHeaders headers,
                                           MultiValueMap<String, String> requestParams) {
        return Mono.just(input);
    }

    default Mono<ExecutionResult> postHandle(ExecutionResult result,
                                             HttpHeaders httpHeaders,
                                             MultiValueMap<String, String> requestParams) {
        return Mono.just(result);
    }

    default Mono<GraphQLHttpResponse> customizeResponseBody(GraphQLHttpResponse graphQLHttpResponse,
                                                            ExecutionResult executionResult,
                                                            HttpHeaders httpHeader,
                                                            MultiValueMap<String, String> requestParams) {
        return Mono.just(graphQLHttpResponse);
    }

}
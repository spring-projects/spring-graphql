package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;

import org.springframework.http.HttpHeaders;

public interface GraphQLInterceptor {

	ExecutionInput preHandle(ExecutionInput input, HttpHeaders headers);

	ExecutionResult postHandle(ExecutionResult result);
}
package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.PublicApi;
import org.springframework.web.context.request.WebRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Lets you customize the #ExecutionInput before the query is executed.
 * You can for example set a context object or define a root value.
 * <p>
 * This is only used if you use the default {@link GraphQLInvocation}.
 */
@PublicApi
public interface ExecutionInputCustomizer {

    CompletableFuture<ExecutionInput> customizeExecutionInput(ExecutionInput executionInput, WebRequest webRequest);

}

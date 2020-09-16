package org.springframework.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

public class WebFluxGraphQLHandler {

	private final GraphQL graphQL;

	public WebFluxGraphQLHandler(GraphQL.Builder graphQLBuilder) {
		this.graphQL = graphQLBuilder.build();
	}

	public Mono<ServerResponse> handle(ServerRequest request) {
		return request.bodyToMono(RequestInput.class)
				.flatMap(body -> {
					String query = body.getQuery();
					if (query == null) {
						query = "";
					}
					ExecutionInput executionInput = ExecutionInput.newExecutionInput()
							.query(query)
							.operationName(body.getOperationName())
							.variables(body.getVariables())
							.build();
					// Invoke GraphQLInterceptor's preHandle here
					return customizeExecutionInput(executionInput, request.headers().asHttpHeaders());
				})
				.flatMap(input -> {
					// Invoke GraphQLInterceptor's postHandle here
					return execute(input);
				})
				.flatMap(result -> ServerResponse.ok().bodyValue(result.toSpecification()));
	}

	protected Mono<ExecutionInput> customizeExecutionInput(ExecutionInput input, HttpHeaders headers) {
		return Mono.just(input);
	}

	protected Mono<ExecutionResult> execute(ExecutionInput input) {
		return Mono.fromFuture(graphQL.executeAsync(input));
	}

}

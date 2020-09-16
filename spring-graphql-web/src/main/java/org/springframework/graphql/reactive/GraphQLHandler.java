package org.springframework.graphql.reactive;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQLRequestBody;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

public class GraphQLHandler {

	private final GraphQL graphQL;

	public GraphQLHandler(GraphQL.Builder graphQLBuilder) {
		this.graphQL = graphQLBuilder.build();
	}

	public Mono<ServerResponse> handle(ServerRequest request) {
		Mono<GraphQLRequestBody> bodyMono = request.bodyToMono(GraphQLRequestBody.class);
		return bodyMono.map(body -> {
			String query = body.getQuery();
			if (query == null) {
				query = "";
			}
			ExecutionInput executionInput = ExecutionInput.newExecutionInput()
					.query(query)
					.operationName(body.getOperationName())
					.variables(body.getVariables())
					.build();
			return customizeExecutionInput(executionInput, request.headers().asHttpHeaders())
					.then(execute(executionInput));
		})
				.flatMap(this::toServerResponse);
	}

	protected Mono<ExecutionInput> customizeExecutionInput(ExecutionInput input, HttpHeaders headers) {
		return Mono.just(input);
	}

	protected Mono<ExecutionResult> execute(ExecutionInput input) {
		return Mono.fromFuture((graphQL.executeAsync(input)));
	}

	protected Mono<ServerResponse> toServerResponse(Mono<ExecutionResult> result) {
		return result.map(ExecutionResult::toSpecification)
				.flatMap(spec -> ServerResponse.ok().bodyValue(spec));
	}

}

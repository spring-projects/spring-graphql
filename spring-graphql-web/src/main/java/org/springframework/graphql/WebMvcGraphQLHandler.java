package org.springframework.graphql;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletException;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class WebMvcGraphQLHandler {

	private final GraphQL graphQL;

	public WebMvcGraphQLHandler(GraphQL.Builder graphQL) {
		this.graphQL = graphQL.build();
	}

	public ServerResponse handle(ServerRequest serverRequest) {
		RequestInput body;
		try {
			body = serverRequest.body(RequestInput.class);
		}
		catch (ServletException | IOException ex) {
			throw new ServerWebInputException("Failed to read request body", null, ex);
		}
		String query = body.getQuery();
		if (query == null) {
			query = "";
		}
		ExecutionInput input = ExecutionInput.newExecutionInput()
				.query(query)
				.operationName(body.getOperationName())
				.variables(body.getVariables())
				.build();

		// Invoke GraphQLInterceptor's preHandle here

		CompletableFuture<Map<String, Object>> future =
				customizeExecutionInput(input, serverRequest.headers().asHttpHeaders())
						.thenCompose(this::execute)
						.thenApply(ExecutionResult::toSpecification);

		// Invoke GraphQLInterceptor's postHandle here

		return future.isDone() ?
				ServerResponse.ok().body(getResult(future)) :
				ServerResponse.ok().body(future);
	}

	protected CompletableFuture<ExecutionInput> customizeExecutionInput(ExecutionInput input, HttpHeaders headers) {
		return CompletableFuture.completedFuture(input);
	}

	protected CompletableFuture<ExecutionResult> execute(ExecutionInput input) {
		return graphQL.executeAsync(input);
	}

	private Map<String, Object> getResult(CompletableFuture<Map<String, Object>> future) {
		try {
			return future.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			throw new ServerErrorException("Failed to get result", ex);
		}
	}
}
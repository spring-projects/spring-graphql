package org.springframework.graphql.servlet;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletException;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;

import org.springframework.graphql.GraphQLRequestBody;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class GraphQLHandler {

	private final GraphQL graphQL;

	public GraphQLHandler(GraphQL.Builder graphQL) {
		this.graphQL = graphQL.build();
	}

	public ServerResponse handle(ServerRequest serverRequest) {
		GraphQLRequestBody body;
		try {
			body = serverRequest.body(GraphQLRequestBody.class);
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
		CompletableFuture<ExecutionResult> resultFuture =
				customizeExecutionInput(input, serverRequest.headers().asHttpHeaders()).thenCompose(this::execute);
		// Invoke GraphQLInterceptor's postHandle here
		return customizeExecutionResult(resultFuture);
	}

	protected CompletableFuture<ExecutionInput> customizeExecutionInput(ExecutionInput input, HttpHeaders headers) {
		return CompletableFuture.completedFuture(input);
	}

	protected CompletableFuture<ExecutionResult> execute(ExecutionInput input) {
		return graphQL.executeAsync(input);
	}

	protected ServerResponse customizeExecutionResult(CompletableFuture<ExecutionResult> resultFuture) {
		return resultFuture.isDone() ?
				ServerResponse.ok().body(getResult(resultFuture)) :
				ServerResponse.ok().body(resultFuture);
	}

	private ExecutionResult getResult(CompletableFuture<ExecutionResult> resultFuture) {
		try {
			return resultFuture.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			throw new ServerErrorException("Failed to get result", ex);
		}
	}
}
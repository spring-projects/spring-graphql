/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler to be exposed as a WebMvc.fn endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 */
public class WebMvcGraphQLHandler implements HandlerFunction<ServerResponse> {

	private final GraphQL graphQL;

	public WebMvcGraphQLHandler(GraphQL.Builder graphQL) {
		this.graphQL = graphQL.build();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws ServletException may be raised when reading the request body,
	 * e.g. {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handle(ServerRequest request) throws ServletException {
		RequestInput requestInput;
		try {
			requestInput = request.body(RequestInput.class);
			requestInput.validate();
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}

		ExecutionInput executionInput = ExecutionInput.newExecutionInput()
				.query(requestInput.getQuery())
				.operationName(requestInput.getOperationName())
				.variables(requestInput.getVariables())
				.build();

		// Invoke GraphQLInterceptor's preHandle here

		CompletableFuture<Map<String, Object>> future =
				customizeExecutionInput(executionInput, request.headers().asHttpHeaders())
						.thenCompose(this::execute)
						.thenApply(ExecutionResult::toSpecification);

		// Invoke GraphQLInterceptor's postHandle here

		return ServerResponse.ok().body(future.isDone() ? getResult(future) : future);
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
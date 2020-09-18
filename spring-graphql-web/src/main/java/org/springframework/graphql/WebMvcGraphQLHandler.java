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

import javax.servlet.ServletException;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

import org.springframework.web.HttpMediaTypeNotSupportedException;
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
		WebInput webInput = createWebInput(request);

		ExecutionInput executionInput = ExecutionInput.newExecutionInput()
				.query(webInput.getQuery())
				.operationName(webInput.getOperationName())
				.variables(webInput.getVariables())
				.build();

		Mono<Map<String, Object>> body = extendInput(executionInput, webInput)
				.flatMap(this::execute)
				.map(ExecutionResult::toSpecification);

		return ServerResponse.ok().body(body);
	}

	private static WebInput createWebInput(ServerRequest request) throws ServletException {
		Map<String, Object> body;
		try {
			body = request.body(WebInput.MAP_PARAMETERIZED_TYPE_REF);
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}
		return new WebInput(request.uri(), request.headers().asHttpHeaders(), body);
	}

	protected Mono<ExecutionInput> extendInput(ExecutionInput executionInput, WebInput webInput) {
		return Mono.just(executionInput);
	}

	protected Mono<ExecutionResult> execute(ExecutionInput input) {
		return Mono.fromFuture(this.graphQL.executeAsync(input));
	}

}
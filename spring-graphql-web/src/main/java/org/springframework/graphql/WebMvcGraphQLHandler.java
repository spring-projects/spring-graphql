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
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import graphql.ExecutionResult;
import graphql.GraphQL;
import reactor.core.publisher.Mono;

import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler to expose as a WebMvc.fn endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 */
public class WebMvcGraphQLHandler implements HandlerFunction<ServerResponse> {

	private final WebInterceptorExecution executionChain;


	public WebMvcGraphQLHandler(GraphQL graphQL, List<WebInterceptor> interceptors) {
		this.executionChain = new WebInterceptorExecution(graphQL, interceptors);
	}


	/**
	 * {@inheritDoc}
	 *
	 * @throws ServletException may be raised when reading the request body,
	 * e.g. {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handle(ServerRequest request) throws ServletException {
		WebInput webInput = new WebInput(request.uri(), request.headers().asHttpHeaders(), readBody(request));
		Mono<WebOutput> outputMono = this.executionChain.execute(webInput);
		return ServerResponse.ok().body(outputMono.map(ExecutionResult::toSpecification));
	}

	private static Map<String, Object> readBody(ServerRequest request) throws ServletException {
		try {
			return request.body(WebInput.MAP_PARAMETERIZED_TYPE_REF);
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}
	}

}
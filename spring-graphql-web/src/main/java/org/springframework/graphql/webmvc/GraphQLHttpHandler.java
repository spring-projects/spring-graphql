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
package org.springframework.graphql.webmvc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import graphql.GraphQL;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.WebInput;
import org.springframework.graphql.WebInterceptor;
import org.springframework.graphql.WebInterceptorExecutionChain;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler to expose as a WebMvc.fn endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 */
public class GraphQLHttpHandler {

	private final static Log logger = LogFactory.getLog(GraphQLHttpHandler.class);

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final WebInterceptorExecutionChain executionChain;


	/**
	 * Create a handler that executes queries through the given {@link GraphQL}
	 * and and invokes the given interceptors to customize input to and the
	 * result from the execution of the query.
	 * @param graphQL the GraphQL instance to use for query execution
	 * @param interceptors 0 or more interceptors to customize input and output
	 */
	public GraphQLHttpHandler(GraphQL graphQL, List<WebInterceptor> interceptors) {
		this.executionChain = new WebInterceptorExecutionChain(graphQL, interceptors);
	}


	/**
	 * {@inheritDoc}
	 *
	 * @throws ServletException may be raised when reading the request body,
	 * e.g. {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handle(ServerRequest request) throws ServletException {
		WebInput webInput = new WebInput(request.uri(), request.headers().asHttpHeaders(), readBody(request));
		if (logger.isDebugEnabled()) {
			logger.debug("Executing: " + webInput);
		}
		Mono<ServerResponse> responseMono = this.executionChain.execute(webInput)
				.map(output -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Execution complete");
					}
					ServerResponse.BodyBuilder builder = ServerResponse.ok();
					if (output.getHeaders() != null) {
						builder.headers(headers -> headers.putAll(output.getHeaders()));
					}
					return builder.body(output.toSpecification());
				});
		return ServerResponse.async(responseMono);
	}

	private static Map<String, Object> readBody(ServerRequest request) throws ServletException {
		try {
			return request.body(MAP_PARAMETERIZED_TYPE_REF);
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}
	}

}
/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.graphql.web.webmvc;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.IdGenerator;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler to expose as a WebMvc.fn endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class GraphQlHttpHandler {

	private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {};

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

	private final WebGraphQlHandler graphQlHandler;

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
	}

	/**
	 * Handle GraphQL requests over HTTP.
	 * @param request the incoming HTTP request
	 * @return the HTTP response
	 * @throws ServletException may be raised when reading the request body, e.g.
	 * {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handleRequest(ServerRequest request) throws ServletException {

		WebInput input = new WebInput(
				request.uri(), request.headers().asHttpHeaders(), readBody(request),
				LocaleContextHolder.getLocale(), this.idGenerator.generateId().toString());

		if (logger.isDebugEnabled()) {
			logger.debug("Executing: " + input);
		}

		Mono<ServerResponse> responseMono = this.graphQlHandler.handleRequest(input).map((output) -> {
			if (logger.isDebugEnabled()) {
				logger.debug("Execution complete");
			}
			ServerResponse.BodyBuilder builder = ServerResponse.ok();
			builder.headers(headers -> headers.putAll(output.getResponseHeaders()));
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

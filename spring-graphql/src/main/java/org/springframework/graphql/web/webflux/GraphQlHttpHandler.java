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

package org.springframework.graphql.web.webflux;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.IdGenerator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * WebFlux.fn Handler for GraphQL over HTTP requests.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class GraphQlHttpHandler {

	private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {
			};

	private final WebGraphQlHandler graphQlHandler;

	private final IdGenerator idGenerator;

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		this(graphQlHandler, new AlternativeJdkIdGenerator());
	}

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @param idGenerator Id generator for requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, IdGenerator idGenerator) {
		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		Assert.notNull(idGenerator, "IdGenerator is required");
		this.graphQlHandler = graphQlHandler;
		this.idGenerator = idGenerator;
	}

	/**
	 * Handle GraphQL requests over HTTP.
	 * @param request the incoming HTTP request
	 * @return the HTTP response
	 */
	public Mono<ServerResponse> handleRequest(ServerRequest request) {
		return request.bodyToMono(MAP_PARAMETERIZED_TYPE_REF)
				.flatMap((body) -> {
					WebInput input = new WebInput(
							request.uri(), request.headers().asHttpHeaders(), body,
							request.exchange().getLocaleContext().getLocale(),
							this.idGenerator.generateId().toString());
					if (logger.isDebugEnabled()) {
						logger.debug("Executing: " + input);
					}
					return this.graphQlHandler.handleRequest(input);
				})
				.flatMap((output) -> {
					Map<String, Object> spec = output.toSpecification();
					if (logger.isDebugEnabled()) {
						logger.debug("Execution complete");
					}
					ServerResponse.BodyBuilder builder = ServerResponse.ok();
					if (output.getResponseHeaders() != null) {
						builder.headers((headers) -> headers.putAll(output.getResponseHeaders()));
					}
					return builder.bodyValue(spec);
				});
	}

}

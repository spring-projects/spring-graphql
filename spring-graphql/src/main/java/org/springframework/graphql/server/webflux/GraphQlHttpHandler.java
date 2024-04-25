/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.server.webflux;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * WebFlux.fn Handler for GraphQL over HTTP requests.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class GraphQlHttpHandler extends AbstractGraphQlHttpHandler {

	private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

	@SuppressWarnings("removal")
	private static final List<MediaType> SUPPORTED_MEDIA_TYPES = List.of(
			MediaType.APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL);


	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		super(graphQlHandler, null);
	}

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @param codecConfigurer codec configurer for JSON encoding and decoding
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, CodecConfigurer codecConfigurer) {
		super(graphQlHandler, new HttpCodecDelegate(codecConfigurer));
	}


	/**
	 * Handle GraphQL requests over HTTP.
	 * @param request the incoming HTTP request
	 * @return the HTTP response
	 */
	public Mono<ServerResponse> handleRequest(ServerRequest request) {
		return readRequest(request)
				.flatMap((body) -> {
					WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
							request.uri(), request.headers().asHttpHeaders(),
							request.cookies(), request.remoteAddress().orElse(null),
							request.attributes(), body,
							request.exchange().getRequest().getId(),
							request.exchange().getLocaleContext().getLocale());
					if (logger.isDebugEnabled()) {
						logger.debug("Executing: " + graphQlRequest);
					}
					return this.graphQlHandler.handleRequest(graphQlRequest);
				})
				.flatMap((response) -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Execution result ready");
					}
					return prepareResponse(request, response);
				});
	}

	protected Mono<ServerResponse> prepareResponse(ServerRequest serverRequest, WebGraphQlResponse response) {
		ServerResponse.BodyBuilder builder = ServerResponse.ok();
		builder.headers((headers) -> headers.putAll(response.getResponseHeaders()));
		builder.contentType(selectResponseMediaType(serverRequest));
		return builder.bodyValue((this.codecDelegate != null) ?
				this.codecDelegate.encode(response) : response.toMap());
	}

	private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
		for (MediaType accepted : serverRequest.headers().accept()) {
			if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
				return accepted;
			}
		}
		return MediaType.APPLICATION_JSON;
	}

}

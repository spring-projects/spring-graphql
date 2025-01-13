/*
 * Copyright 2020-2025 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlHandler;
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

	private static final MediaType APPLICATION_GRAPHQL =
			new MediaType("application", "graphql+json");

	private static final List<MediaType> SUPPORTED_MEDIA_TYPES = List.of(
			MediaType.APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, APPLICATION_GRAPHQL);


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
		super(graphQlHandler, codecConfigurer);
	}


	protected Mono<ServerResponse> prepareResponse(ServerRequest request, WebGraphQlResponse response) {
		ServerResponse.BodyBuilder builder = ServerResponse.ok();
		builder.headers((headers) -> headers.putAll(response.getResponseHeaders()));
		builder.contentType(selectResponseMediaType(request));
		return builder.bodyValue(encodeResponseIfNecessary(response));
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

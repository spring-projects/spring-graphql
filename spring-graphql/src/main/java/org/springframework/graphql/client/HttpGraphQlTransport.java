/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.client;

import java.util.Collections;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;


/**
 * Transport to execute GraphQL requests over HTTP via {@link WebClient}.
 *
 * <p>Supports only single-response requests over HTTP POST. For subscriptions,
 * see {@link WebSocketGraphQlTransport} and {@link RSocketGraphQlTransport}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
final class HttpGraphQlTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() { };

	private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_TYPE =
			new ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>>() { };

	private static final MediaType APPLICATION_GRAPHQL =
			new MediaType("application", "graphql+json");


	private final WebClient webClient;

	private final MediaType contentType;


	HttpGraphQlTransport(WebClient webClient) {
		Assert.notNull(webClient, "WebClient is required");
		this.webClient = webClient;
		this.contentType = initContentType(webClient);
	}

	private static MediaType initContentType(WebClient webClient) {
		HttpHeaders headers = new HttpHeaders();
		webClient.mutate().defaultHeaders(headers::putAll);
		MediaType contentType = headers.getContentType();
		return (contentType != null) ? contentType : MediaType.APPLICATION_JSON;
	}


	@Override
	public Mono<GraphQlResponse> execute(GraphQlRequest request) {
		return this.webClient.post()
				.contentType(this.contentType)
				.accept(MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE, APPLICATION_GRAPHQL)
				.bodyValue(request.toMap())
				.attributes((attributes) -> {
					if (request instanceof ClientGraphQlRequest clientRequest) {
						attributes.putAll(clientRequest.getAttributes());
					}
				})
				.exchangeToMono((response) -> {
					if (response.statusCode().equals(HttpStatus.OK)) {
						return response.bodyToMono(MAP_TYPE);
					}
					else if (response.statusCode().is4xxClientError() && isGraphQlResponse(response)) {
						return response.bodyToMono(MAP_TYPE);
					}
					else {
						return response.createError();
					}
				})
				.map(ResponseMapGraphQlResponse::new);
	}

	private static boolean isGraphQlResponse(ClientResponse clientResponse) {
		return MediaTypes.APPLICATION_GRAPHQL_RESPONSE
				.isCompatibleWith(clientResponse.headers().contentType().orElse(null));
	}

	@Override
	public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
		return this.webClient.post()
				.contentType(this.contentType)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(request.toMap())
				.attributes((attributes) -> {
					if (request instanceof ClientGraphQlRequest clientRequest) {
						attributes.putAll(clientRequest.getAttributes());
					}
				})
				.retrieve()
				.bodyToFlux(SSE_TYPE)
				.takeWhile((event) -> "next".equals(event.event()))
				.map((event) -> {
					Map<String, Object> data = (event.data() != null) ? event.data() : Collections.emptyMap();
					return new ResponseMapGraphQlResponse(data);
				});
	}

}

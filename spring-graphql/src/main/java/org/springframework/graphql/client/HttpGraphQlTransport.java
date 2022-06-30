/*
 * Copyright 2002-2022 the original author or authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.graphql.client.MultipartBodyCreator.convertRequestToMultipartData;


/**
 * Transport to execute GraphQL requests over HTTP via {@link WebClient}.
 *
 * <p>Supports only single-response requests over HTTP POST. For subscriptions,
 * see {@link WebSocketGraphQlTransport} and {@link RSocketGraphQlTransport}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class HttpGraphQlTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


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
		return (contentType != null ? contentType : MediaType.APPLICATION_JSON);
	}


	@Override
	public Mono<GraphQlResponse> execute(GraphQlRequest request) {
		return this.webClient.post()
				.contentType(this.contentType)
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL)
				.bodyValue(request.toMap())
				.retrieve()
				.bodyToMono(MAP_TYPE)
				.map(ResponseMapGraphQlResponse::new);
	}

    @Override
    public Mono<GraphQlResponse> executeFileUpload(GraphQlRequest request) {
        return this.webClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL)
                .body(BodyInserters.fromMultipartData(convertRequestToMultipartData(request)))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(ResponseMapGraphQlResponse::new);
    }


    @Override
	public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
		throw new UnsupportedOperationException("Subscriptions not supported over HTTP");
	}

}

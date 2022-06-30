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

package org.springframework.graphql.test.tester;

import java.util.Collections;
import java.util.Map;

import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;

import static org.springframework.graphql.client.MultipartBodyCreator.convertRequestToMultipartData;

/**
 * {@code GraphQlTransport} for GraphQL over HTTP via {@link WebTestClient}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class WebTestClientTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final WebTestClient webTestClient;


	WebTestClientTransport(WebTestClient webTestClient) {
		Assert.notNull(webTestClient, "WebTestClient is required");
		this.webTestClient = webTestClient;
	}


	@Override
	public Mono<GraphQlResponse> execute(GraphQlRequest request) {

		Map<String, Object> responseMap = this.webTestClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(request.toMap())
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody(MAP_TYPE)
				.returnResult()
				.getResponseBody();

		responseMap = (responseMap != null ? responseMap : Collections.emptyMap());
		GraphQlResponse response = GraphQlTransport.createResponse(responseMap);
		return Mono.just(response);
	}

    @Override
    public Mono<GraphQlResponse> executeFileUpload(GraphQlRequest request) {

        Map<String, Object> responseMap = this.webTestClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromMultipartData(convertRequestToMultipartData(request)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(MAP_TYPE)
                .returnResult()
                .getResponseBody();

        responseMap = (responseMap != null ? responseMap : Collections.emptyMap());
        GraphQlResponse response = GraphQlTransport.createResponse(responseMap);
        return Mono.just(response);
    }

	@Override
	public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
		throw new UnsupportedOperationException("Subscriptions not supported over HTTP");
	}

}

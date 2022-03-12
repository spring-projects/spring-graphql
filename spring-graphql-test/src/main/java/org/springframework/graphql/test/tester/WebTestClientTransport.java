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

import graphql.ExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.support.MapExecutionResult;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;

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
	public Mono<ExecutionResult> execute(GraphQlRequest request) {

		Map<String, Object> resultMap = this.webTestClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(request.toMap())
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody(MAP_TYPE)
				.returnResult()
				.getResponseBody();

		resultMap = (resultMap != null ? resultMap : Collections.emptyMap());
		ExecutionResult result = MapExecutionResult.from(resultMap);
		return Mono.just(result);
	}

	@Override
	public Flux<ExecutionResult> executeSubscription(GraphQlRequest request) {
		throw new UnsupportedOperationException("Subscriptions not supported over HTTP");
	}

}

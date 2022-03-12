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

import java.util.Map;

import graphql.ExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.support.MapExecutionResult;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;


/**
 * Transport to execute GraphQL requests over HTTP via {@link WebClient}.
 * Supports only single-response requests over HTTP POST. For subscription
 * requests, see {@link WebSocketGraphQlTransport}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class HttpGraphQlTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final WebClient webClient;


	HttpGraphQlTransport(WebClient webClient) {
		Assert.notNull(webClient, "WebClient is required");
		this.webClient = webClient;
	}


	@Override
	public Mono<ExecutionResult> execute(GraphQlRequest request) {
		return this.webClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(request.toMap())
				.retrieve()
				.bodyToMono(MAP_TYPE)
				.map(MapExecutionResult::from);
	}

	@Override
	public Flux<ExecutionResult> executeSubscription(GraphQlRequest request) {
		throw new UnsupportedOperationException("Subscriptions not supported over HTTP");
	}

}

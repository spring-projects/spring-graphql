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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.Assert;


/**
 * Transport to execute GraphQL requests over RSocket via {@link RSocketRequester}.
 *
 * <p>Servers are expected to support the
 * <a href="https://github.com/rsocket/rsocket/blob/master/Extensions/Routing.md">Routing</a>
 * metadata extension.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class RSocketGraphQlTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final String route;

	private final RSocketRequester rsocketRequester;


	RSocketGraphQlTransport(String route, RSocketRequester requester) {
		Assert.notNull(route, "'route' is required");
		Assert.notNull(requester, "RSocketRequester is required");
		this.route = route;
		this.rsocketRequester = requester;
	}


	@Override
	public Mono<GraphQlResponse> execute(GraphQlRequest request) {
		return this.rsocketRequester.route(this.route).data(request.toMap())
				.retrieveMono(MAP_TYPE)
				.map(ResponseMapGraphQlResponse::new);
	}

	@Override
	public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
		return this.rsocketRequester.route(this.route).data(request.toMap())
				.retrieveFlux(MAP_TYPE)
				.map(ResponseMapGraphQlResponse::new);
	}

}

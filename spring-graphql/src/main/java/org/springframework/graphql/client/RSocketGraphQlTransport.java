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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import io.rsocket.exceptions.RejectedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
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

	private static final ResolvableType LIST_TYPE = ResolvableType.forClass(List.class);


	private final String route;

	private final RSocketRequester rsocketRequester;

	private final Decoder<?> jsonDecoder;


	RSocketGraphQlTransport(String route, RSocketRequester requester, Decoder<?> jsonDecoder) {
		Assert.notNull(route, "'route' is required");
		Assert.notNull(requester, "RSocketRequester is required");
		Assert.notNull(jsonDecoder, "JSON Decoder is required");
		this.route = route;
		this.rsocketRequester = requester;
		this.jsonDecoder = jsonDecoder;
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
				.onErrorResume(RejectedException.class, ex -> Flux.error(decodeErrors(request, ex)))
				.map(ResponseMapGraphQlResponse::new);
	}

    @Override
    public Mono<GraphQlResponse> executeFileUpload(GraphQlRequest request) {
        throw new UnsupportedOperationException("File upload is not supported");
    }

	@SuppressWarnings("unchecked")
	private Exception decodeErrors(GraphQlRequest request, RejectedException ex) {
		try {
			byte[] errorData = ex.getMessage().getBytes(StandardCharsets.UTF_8);
			List<GraphQLError> errors = (List<GraphQLError>) this.jsonDecoder.decode(
					DefaultDataBufferFactory.sharedInstance.wrap(errorData), LIST_TYPE, null, null);
			GraphQlResponse response = new ResponseMapGraphQlResponse(Collections.singletonMap("errors", errors));
			return new SubscriptionErrorException(request, response.getErrors());
		}
		catch (DecodingException ex2) {
			return ex;
		}
	}

}

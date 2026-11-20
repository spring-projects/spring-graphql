/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
 */
final class RSocketGraphQlTransport implements GraphQlTransport {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() { };

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
				.onErrorResume(RejectedException.class, (ex) -> Flux.error(decodeErrors(request, ex)))
				.map(ResponseMapGraphQlResponse::new);
	}

	@SuppressWarnings("unchecked")
	private Exception decodeErrors(GraphQlRequest request, RejectedException ex) {
		try {
			String errorMessage = (ex.getMessage() != null) ? ex.getMessage() : "";
			byte[] errorData = errorMessage.getBytes(StandardCharsets.UTF_8);
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

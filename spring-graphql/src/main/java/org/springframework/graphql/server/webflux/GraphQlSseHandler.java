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


import java.util.Collections;
import java.util.Map;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * GraphQL handler that supports the
 * <a href="https://github.com/graphql/graphql-over-http/blob/main/rfcs/GraphQLOverSSE.md">GraphQL
 * Server-Sent Events Protocol</a> and to be exposed as a WebFlux.fn endpoint via
 * {@link org.springframework.web.reactive.function.server.RouterFunctions}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public class GraphQlSseHandler extends AbstractGraphQlHttpHandler {

	private static final Mono<ServerSentEvent<Map<String, Object>>> COMPLETE_EVENT = Mono.just(
			ServerSentEvent.<Map<String, Object>>builder(Collections.emptyMap()).event("complete").build());


	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
		super(graphQlHandler, null);
	}


	@SuppressWarnings("unchecked")
	@Override
	protected Mono<ServerResponse> prepareResponse(ServerRequest request, WebGraphQlResponse response) {

		Flux<Map<String, Object>> resultFlux;
		if (response.getData() instanceof Publisher) {
			resultFlux = Flux.from((Publisher<ExecutionResult>) response.getData())
					.map(ExecutionResult::toSpecification)
					.onErrorResume(SubscriptionPublisherException.class, (ex) -> Mono.just(ex.toMap()));
		}
		else {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("A subscription DataFetcher must return a Publisher: " + response.getData());
			}
			resultFlux = Flux.just(ExecutionResult.newExecutionResult()
					.addError(GraphQLError.newError()
							.errorType(ErrorType.OperationNotSupported)
							.message("SSE handler supports only subscriptions")
							.build())
					.build()
					.toSpecification());
		}

		Flux<ServerSentEvent<Map<String, Object>>> sseFlux =
				resultFlux.map((event) -> ServerSentEvent.builder(event).event("next").build());

		return ServerResponse.ok()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.body(BodyInserters.fromServerSentEvents(sseFlux.concatWith(COMPLETE_EVENT)))
				.onErrorResume(Throwable.class, (ex) -> ServerResponse.badRequest().build());
	}

}

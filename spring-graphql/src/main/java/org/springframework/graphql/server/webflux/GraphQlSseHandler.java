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


import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.Nullable;
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

	@Nullable
	private final Duration timeout;


	/**
	 * Constructor with the handler to delegate to, and no timeout by default,
	 * which results in never timing out.
	 * @param graphQlHandler the handler to delegate to
	 */
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
		this(graphQlHandler, null);
	}

	/**
	 * Variant constructor with a timeout to use for SSE subscriptions.
	 * @param graphQlHandler the handler to delegate to
	 * @param timeout the timeout value to use or {@code null} to never time out
	 * @since 1.3.3
	 */
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler, @Nullable Duration timeout) {
		super(graphQlHandler, null);
		this.timeout = timeout;
	}


	@SuppressWarnings("unchecked")
	@Override
	protected Mono<ServerResponse> prepareResponse(ServerRequest request, WebGraphQlResponse response) {

		Flux<Map<String, Object>> resultFlux;
		if (response.getData() instanceof Publisher) {
			resultFlux = Flux.from((Publisher<ExecutionResult>) response.getData())
					.map(ExecutionResult::toSpecification)
					.onErrorResume(this::exceptionToResultMap);
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
				resultFlux.map((event) -> ServerSentEvent.builder(event).event("next").build())
						.concatWith(COMPLETE_EVENT);

		Mono<ServerResponse> responseMono = ServerResponse.ok()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.body(BodyInserters.fromServerSentEvents(sseFlux))
				.onErrorResume(Throwable.class, (ex) -> ServerResponse.badRequest().build());

		return ((this.timeout != null) ? responseMono.timeout(this.timeout) : responseMono);
	}

	private Mono<Map<String, Object>> exceptionToResultMap(Throwable ex) {
		return Mono.just((ex instanceof SubscriptionPublisherException spe) ?
				spe.toMap() :
				GraphqlErrorBuilder.newError()
						.message("Subscription error")
						.errorType(org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR)
						.build()
						.toSpecification());
	}

}

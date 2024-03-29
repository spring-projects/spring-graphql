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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
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
 * @since 1.3.0
 */
public class GraphQlSseHandler {

    private static final Log logger = LogFactory.getLog(GraphQlSseHandler.class);

    private static final Mono<ServerSentEvent<Map<String, Object>>> COMPLETE_EVENT = Mono.just(ServerSentEvent.<Map<String, Object>>builder(Collections.emptyMap()).event("complete").build());

    private final WebGraphQlHandler graphQlHandler;


    public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
        Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
        this.graphQlHandler = graphQlHandler;
    }

    /**
     * Handle GraphQL requests over HTTP using the Server-Sent Events protocol.
     *
     * @param serverRequest the incoming HTTP request
     * @return the HTTP response
     */
    @SuppressWarnings("unchecked")
    public Mono<ServerResponse> handleRequest(ServerRequest serverRequest) {
        Flux<ServerSentEvent<Map<String, Object>>> data = serverRequest.bodyToMono(SerializableGraphQlRequest.class)
                .flatMap(body -> {
                    WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
                            serverRequest.uri(), serverRequest.headers().asHttpHeaders(),
                            serverRequest.cookies(), serverRequest.attributes(), body,
                            serverRequest.exchange().getRequest().getId(),
                            serverRequest.exchange().getLocaleContext().getLocale());
                    if (logger.isDebugEnabled()) {
                        logger.debug("Executing: " + graphQlRequest);
                    }
                    return this.graphQlHandler.handleRequest(graphQlRequest);
                })
                .flatMapMany(response -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Execution result ready"
                                + (!CollectionUtils.isEmpty(response.getErrors()) ? " with errors: " + response.getErrors() : "")
                                + ".");
                    }
                    if (response.getData() instanceof Publisher) {
                        // Subscription
                        return Flux.from((Publisher<ExecutionResult>) response.getData()).map(ExecutionResult::toSpecification);
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Only subscriptions are supported, DataFetcher must return a Publisher type");
                    }
                    // Single response (query or mutation) are not supported
                    String errorMessage = "SSE transport only supports Subscription operations";
                    GraphQLError unsupportedOperationError = GraphQLError.newError().errorType(ErrorType.OperationNotSupported)
                            .message(errorMessage).build();
                    return Flux.error(new SubscriptionPublisherException(Collections.singletonList(unsupportedOperationError),
                            new IllegalArgumentException(errorMessage)));
                })
                .onErrorResume(SubscriptionPublisherException.class, exc -> {
                    ExecutionResult errorResult = ExecutionResult.newExecutionResult().errors(exc.getErrors()).build();
                    return Flux.just(errorResult.toSpecification());
                })
                .map(event -> ServerSentEvent.builder(event).event("next").build());

        Flux<ServerSentEvent<Map<String, Object>>> body = data.concatWith(COMPLETE_EVENT);
        return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(BodyInserters.fromServerSentEvents(body))
                .onErrorResume(Throwable.class, exc -> ServerResponse.badRequest().build());
    }

}

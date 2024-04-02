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

package org.springframework.graphql.server.webmvc;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import jakarta.servlet.ServletException;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.IdGenerator;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler that supports the
 * <a href="https://github.com/graphql/graphql-over-http/blob/main/rfcs/GraphQLOverSSE.md">GraphQL
 * Server-Sent Events Protocol</a> and to be exposed as a WebMvc functional endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 *
 * @author Brian Clozel
 * @since 1.3.0
 */
public class GraphQlSseHandler extends AbstractGraphQlHttpHandler {

    private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();


    public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
        super(graphQlHandler, null);
    }

    /**
     * Handle GraphQL requests over HTTP using the Server-Sent Events protocol.
     *
     * @param serverRequest the incoming HTTP request
     * @return the HTTP response
     * @throws ServletException may be raised when reading the request body, e.g.
     * {@link HttpMediaTypeNotSupportedException}.
     */
    public ServerResponse handleRequest(ServerRequest serverRequest) throws ServletException {

        WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
                serverRequest.uri(), serverRequest.headers().asHttpHeaders(), initCookies(serverRequest),
                serverRequest.attributes(), readBody(serverRequest), this.idGenerator.generateId().toString(),
                LocaleContextHolder.getLocale());

        if (logger.isDebugEnabled()) {
            logger.debug("Executing: " + graphQlRequest);
        }
        return ServerResponse.sse(sseBuilder -> {
            this.graphQlHandler.handleRequest(graphQlRequest)
                    .flatMapMany(this::handleResponse)
                    .subscribe(new SendMessageSubscriber(graphQlRequest.getId(), sseBuilder));
        });
    }


    @SuppressWarnings("unchecked")
    private Publisher<Map<String, Object>> handleResponse(WebGraphQlResponse response) {
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
    }


    private static class SendMessageSubscriber extends BaseSubscriber<Map<String, Object>> {

        final String id;

        final ServerResponse.SseBuilder sseBuilder;

        public SendMessageSubscriber(String id, ServerResponse.SseBuilder sseBuilder) {
            this.id = id;
            this.sseBuilder = sseBuilder;
        }

        @Override
        protected void hookOnNext(Map<String, Object> value) {
            writeNext(value);
        }

        @Override
        protected void hookOnError(Throwable throwable) {
            if (throwable instanceof SubscriptionPublisherException subscriptionException) {
                ExecutionResult errorResult = ExecutionResult.newExecutionResult().errors(subscriptionException.getErrors()).build();
                writeNext(errorResult.toSpecification());
            }
            else {
                this.sseBuilder.error(throwable);
            }
            this.hookOnComplete();
        }

        private void writeNext(Map<String, Object> value) {
            try {
                this.sseBuilder.event("next");
                this.sseBuilder.data(value);
            } catch (IOException exception) {
                this.onError(exception);
            }
        }

        @Override
        protected void hookOnComplete() {
            try {
                this.sseBuilder.event("complete").data("");
            } catch (IOException exc) {
                throw new RuntimeException(exc);
            }
            this.sseBuilder.complete();
        }

    }

}

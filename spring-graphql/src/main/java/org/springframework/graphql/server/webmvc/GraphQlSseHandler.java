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
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler that supports the
 * <a href="https://github.com/graphql/graphql-over-http/blob/main/rfcs/GraphQLOverSSE.md">GraphQL
 * Server-Sent Events Protocol</a> and to be exposed as a WebMvc functional endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public class GraphQlSseHandler extends AbstractGraphQlHttpHandler {

	@Nullable
	private final Duration timeout;


	/**
	 * Constructor with the handler to delegate to, and no timeout,
	 * i.e. relying on underlying Server async request timeout.
	 * @param graphQlHandler the handler to delegate to
	 */
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
		this(graphQlHandler, null);
	}

	/**
	 * Variant constructor with a timeout to use for SSE subscriptions.
	 * @param graphQlHandler the handler to delegate to
	 * @param timeout the timeout value to set on
	 * {@link org.springframework.web.context.request.async.AsyncWebRequest#setTimeout(Long)}
	 * @since 1.3.3
	 */
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler, @Nullable Duration timeout) {
		super(graphQlHandler, null);
		this.timeout = timeout;
	}


	@Override
	protected ServerResponse prepareResponse(
			ServerRequest request, Mono<WebGraphQlResponse> responseMono) {

		Flux<Map<String, Object>> resultFlux = responseMono.flatMapMany((response) -> {

			if (response.getData() instanceof Publisher) {
				Publisher<ExecutionResult> publisher = response.getData();
				return Flux.from(publisher).map(ExecutionResult::toSpecification);
			}

			if (this.logger.isDebugEnabled()) {
				this.logger.debug("A subscription DataFetcher must return a Publisher: " + response.getData());
			}

			return Flux.just(ExecutionResult.newExecutionResult()
					.addError(GraphQLError.newError()
							.errorType(ErrorType.OperationNotSupported)
							.message("SSE handler supports only subscriptions")
							.build())
					.build()
					.toSpecification());
		});

		return ((this.timeout != null) ?
				ServerResponse.sse(SseSubscriber.connect(resultFlux), this.timeout) :
				ServerResponse.sse(SseSubscriber.connect(resultFlux)));
	}


	/**
	 * {@link org.reactivestreams.Subscriber} that writes to {@link ServerResponse.SseBuilder}.
	 */
	private static final class SseSubscriber extends BaseSubscriber<Map<String, Object>> {

		private final ServerResponse.SseBuilder sseBuilder;

		private SseSubscriber(ServerResponse.SseBuilder sseBuilder) {
			this.sseBuilder = sseBuilder;
			this.sseBuilder.onTimeout(() -> cancelWithError(new AsyncRequestTimeoutException()));
		}

		@Override
		protected void hookOnNext(Map<String, Object> value) {
			sendNext(value);
		}

		private void sendNext(Map<String, Object> value) {
			try {
				this.sseBuilder.event("next");
				this.sseBuilder.data(value);
			}
			catch (IOException exception) {
				cancelWithError(exception);
			}
		}

		private void cancelWithError(Throwable ex) {
			this.cancel();
			this.sseBuilder.error(ex);
		}

		@Override
		protected void hookOnError(Throwable ex) {
			sendNext(exceptionToResultMap(ex));
			sendComplete();
		}

		private static Map<String, Object> exceptionToResultMap(Throwable ex) {
			return ((ex instanceof SubscriptionPublisherException spe) ?
					spe.toMap() :
					GraphqlErrorBuilder.newError()
							.message("Subscription error")
							.errorType(org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR)
							.build()
							.toSpecification());
		}

		private void sendComplete() {
			try {
				this.sseBuilder.event("complete").data("");
			}
			catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			this.sseBuilder.complete();
		}

		@Override
		protected void hookOnComplete() {
			sendComplete();
		}

		static Consumer<ServerResponse.SseBuilder> connect(Flux<Map<String, Object>> resultFlux) {
			return (sseBuilder) -> {
				SseSubscriber subscriber = new SseSubscriber(sseBuilder);
				resultFlux.subscribe(subscriber);
			};
		}
	}

}

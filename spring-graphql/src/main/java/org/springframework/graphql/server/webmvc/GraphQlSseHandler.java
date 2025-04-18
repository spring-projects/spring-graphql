/*
 * Copyright 2020-2025 the original author or authors.
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
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

	private static final Map<String, Object> HEARTBEAT_MAP = new LinkedHashMap<>(0);


	private final @Nullable Duration timeout;

	private final @Nullable Duration keepAliveDuration;


	/**
	 * Constructor with the handler to delegate to, and no timeout,
	 * i.e. relying on underlying Server async request timeout.
	 * @param graphQlHandler the handler to delegate to
	 */
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
		this(graphQlHandler, null, null);
	}

	/**
	 * Variant constructor with a timeout to use for SSE subscriptions.
	 * @param graphQlHandler the handler to delegate to
	 * @param timeout the timeout value to set on
	 * {@link org.springframework.web.context.request.async.AsyncWebRequest#setTimeout(Long)}
	 * @since 1.3.3
	 */
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler, @Nullable Duration timeout) {
		this(graphQlHandler, timeout, null);
	}

	/**
	 * Variant constructor with a timeout to use for SSE subscriptions.
	 * @param graphQlHandler the handler to delegate to
	 * @param timeout the timeout value to set on
	 * @param keepAliveDuration how frequently to send empty comment messages
	 * when no other messages are sent
	 * {@link org.springframework.web.context.request.async.AsyncWebRequest#setTimeout(Long)}
	 * @since 1.4.0
	 */
	public GraphQlSseHandler(
			WebGraphQlHandler graphQlHandler, @Nullable Duration timeout, @Nullable Duration keepAliveDuration) {

		super(graphQlHandler, null);
		this.timeout = timeout;
		this.keepAliveDuration = keepAliveDuration;
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
				ServerResponse.sse(SseSubscriber.connect(resultFlux, this.logger, this.keepAliveDuration), this.timeout) :
				ServerResponse.sse(SseSubscriber.connect(resultFlux, this.logger, this.keepAliveDuration)));
	}


	/**
	 * {@link org.reactivestreams.Subscriber} that writes to {@link ServerResponse.SseBuilder}.
	 */
	private static final class SseSubscriber extends BaseSubscriber<Map<String, Object>> {

		private final ServerResponse.SseBuilder sseBuilder;

		private final Log logger;

		private SseSubscriber(ServerResponse.SseBuilder sseBuilder, Log logger) {
			this.sseBuilder = sseBuilder;
			this.sseBuilder.onTimeout(() -> cancelWithError(new AsyncRequestTimeoutException()));
			this.logger = logger;
		}

		@Override
		protected void hookOnNext(Map<String, Object> value) {
			if (value == HEARTBEAT_MAP) {
				sendHeartbeat();
				return;
			}
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

		private void sendHeartbeat() {
			try {
				// Currently, comment cannot be empty:
				// https://github.com/spring-projects/spring-framework/issues/34608
				this.sseBuilder.comment(" ");
				this.sseBuilder.send();
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
			Map<String, Object> errorMap;
			if (ex instanceof SubscriptionPublisherException spe) {
				errorMap = spe.toMap();
			}
			else {
				if (this.logger.isErrorEnabled()) {
					this.logger.error("Unresolved " + ex.getClass().getSimpleName(), ex);
				}
				errorMap = GraphqlErrorBuilder.newError()
						.message("Subscription error")
						.errorType(org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR)
						.build()
						.toSpecification();
			}
			sendNext(errorMap);
			sendComplete();
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

		static Consumer<ServerResponse.SseBuilder> connect(
				Flux<Map<String, Object>> resultFlux, Log logger, @Nullable Duration keepAliveDuration) {

			return (sseBuilder) -> {
				SseSubscriber subscriber = new SseSubscriber(sseBuilder, logger);
				if (keepAliveDuration != null) {
					KeepAliveHandler handler = new KeepAliveHandler(keepAliveDuration);
					handler.compose(resultFlux).subscribe(subscriber);
				}
				else {
					resultFlux.subscribe(subscriber);
				}
			};
		}
	}


	private static final class KeepAliveHandler {

		private final Duration keepAliveDuration;

		private boolean eventSent;

		private final Sinks.Empty<Void> completionSink = Sinks.empty();

		KeepAliveHandler(Duration keepAliveDuration) {
			this.keepAliveDuration = keepAliveDuration;
		}

		public Flux<Map<String, Object>> compose(Flux<Map<String, Object>> flux) {
			return flux.doOnNext((event) -> this.eventSent = true)
					.doOnComplete(this.completionSink::tryEmitEmpty)
					.mergeWith(getKeepAliveFlux())
					.takeUntilOther(this.completionSink.asMono());
		}

		private Flux<Map<String, Object>> getKeepAliveFlux() {
			return Flux.interval(this.keepAliveDuration, this.keepAliveDuration)
					.filter((aLong) -> !checkEventSentAndClear())
					.map((aLong) -> HEARTBEAT_MAP);
		}

		private boolean checkEventSentAndClear() {
			boolean result = this.eventSent;
			this.eventSent = false;
			return result;
		}
	}

}

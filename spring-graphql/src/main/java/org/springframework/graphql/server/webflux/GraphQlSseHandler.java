/*
 * Copyright 2020-present the original author or authors.
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
import java.util.Set;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.language.OperationDefinition;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.Assert;
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

	private static final ServerSentEvent<Map<String, Object>> HEARTBEAT_EVENT =
			ServerSentEvent.<Map<String, Object>>builder().comment("").build();

	private static final Mono<ServerSentEvent<Map<String, Object>>> COMPLETE_EVENT_MONO =
			Mono.just(ServerSentEvent.<Map<String, Object>>builder(Collections.emptyMap()).event("complete").build());

	private static final Set<OperationDefinition.Operation> SUPPORTED_OPERATIONS =
			Set.of(OperationDefinition.Operation.SUBSCRIPTION);

	private static final Set<HttpMethod> SUPPORTED_HTTP_METHODS = Set.of(HttpMethod.GET, HttpMethod.POST);

	private final @Nullable Duration timeout;

	private final @Nullable Duration keepAliveDuration;

	private final Set<HttpMethod> httpMethods;


	/**
	 * Basic constructor with the handler to delegate to, and no timeout by default,
	 * which results in never timing out.
	 * @param graphQlHandler the handler to delegate to
	 * @deprecated since 2.1.0 in favor of {@link #builder(WebGraphQlHandler)}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
		this(graphQlHandler, null, null, Set.of(HttpMethod.POST));
	}

	/**
	 * Constructor with a timeout on how long to wait for the application to return
	 * the {@link ServerResponse} that will start the stream.
	 * @param graphQlHandler the handler to delegate to
	 * @param timeout the timeout value, or {@code null} to never time out
	 * @since 1.3.3
	 * @deprecated since 2.1.0 in favor of {@link #builder(WebGraphQlHandler)}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler, @Nullable Duration timeout) {
		this(graphQlHandler, timeout, null, Set.of(HttpMethod.POST));
	}

	/**
	 * Constructor with a keep-alive duration that determines how frequently to
	 * heartbeats during periods of inactivity.
	 * @param graphQlHandler the handler to delegate to
	 * @param timeout the timeout value to use or {@code null} to never time out
	 * @param keepAliveDuration how frequently to send empty comment messages
	 * when no other messages are sent
	 * @since 1.4.0
	 * @deprecated since 2.1.0 in favor of {@link #builder(WebGraphQlHandler)}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public GraphQlSseHandler(
			WebGraphQlHandler graphQlHandler, @Nullable Duration timeout, @Nullable Duration keepAliveDuration) {

		this(graphQlHandler, timeout, keepAliveDuration, Set.of(HttpMethod.POST));
	}

	private GraphQlSseHandler(WebGraphQlHandler graphQlHandler, @Nullable Duration timeout,
			@Nullable Duration keepAliveDuration, Set<HttpMethod> httpMethods) {

		super(graphQlHandler, null);
		Assert.notEmpty(httpMethods, "'httpMethods' must not be empty");
		Assert.isTrue(SUPPORTED_HTTP_METHODS.containsAll(httpMethods), () ->
				"'httpMethods' must be a subset of " + SUPPORTED_HTTP_METHODS + ", got " + httpMethods);
		this.timeout = timeout;
		this.keepAliveDuration = keepAliveDuration;
		this.httpMethods = httpMethods;
	}

	/**
	 * Return a builder to create a {@link GraphQlSseHandler}, e.g. to configure
	 * which HTTP methods it should support.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @since 2.1.0
	 */
	public static Builder builder(WebGraphQlHandler graphQlHandler) {
		return new Builder(graphQlHandler);
	}

	/**
	 * Return the HTTP methods this handler is configured to support.
	 * <p>Applications should use this value when configuring the matching
	 * {@link GraphQlRequestPredicates#graphQlSse(String, Set) RequestPredicate},
	 * so that both stay in sync.
	 * @since 2.1.0
	 */
	public Set<HttpMethod> getHttpMethods() {
		return this.httpMethods;
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
		else if (!response.getExecutionResult().getErrors().isEmpty()) {
			resultFlux = Flux.just(response.getExecutionResult().toSpecification());
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
						.concatWith(COMPLETE_EVENT_MONO);

		if (this.keepAliveDuration != null) {
			KeepAliveHandler handler = new KeepAliveHandler(this.keepAliveDuration);
			sseFlux = handler.compose(sseFlux);
		}

		Mono<ServerResponse> responseMono = ServerResponse.ok()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.body(BodyInserters.fromServerSentEvents(sseFlux))
				.onErrorResume(Throwable.class, (ex) -> ServerResponse.badRequest().build());

		return ((this.timeout != null) ? responseMono.timeout(this.timeout) : responseMono);
	}

	@Override
	protected Set<OperationDefinition.Operation> getSupportedOperations() {
		return SUPPORTED_OPERATIONS;
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


	private static final class KeepAliveHandler {

		private final Duration keepAliveDuration;

		private boolean eventSent;

		KeepAliveHandler(Duration keepAliveDuration) {
			this.keepAliveDuration = keepAliveDuration;
		}

		public Flux<ServerSentEvent<Map<String, Object>>> compose(Flux<ServerSentEvent<Map<String, Object>>> flux) {
			return flux.doOnNext((event) -> this.eventSent = true)
					.mergeWith(getKeepAliveFlux())
					.takeUntil((sse) -> "complete".equals(sse.event()));
		}

		private Flux<ServerSentEvent<Map<String, Object>>> getKeepAliveFlux() {
			return Flux.interval(this.keepAliveDuration, this.keepAliveDuration)
					.filter((aLong) -> !checkEventSentAndClear())
					.map((aLong) -> HEARTBEAT_EVENT);
		}

		private boolean checkEventSentAndClear() {
			boolean result = this.eventSent;
			this.eventSent = false;
			return result;
		}
	}


	/**
	 * Builder for {@link GraphQlSseHandler}.
	 * @since 2.1.0
	 */
	public static final class Builder {

		private final WebGraphQlHandler graphQlHandler;

		private @Nullable Duration timeout;

		private @Nullable Duration keepAliveDuration;

		private Set<HttpMethod> httpMethods = Set.of(HttpMethod.POST);

		private Builder(WebGraphQlHandler graphQlHandler) {
			this.graphQlHandler = graphQlHandler;
		}

		/**
		 * Set a timeout on how long to wait for the application to return the
		 * {@link ServerResponse} that will start the stream.
		 * @param timeout the timeout value, or {@code null} to never time out
		 */
		public Builder timeout(@Nullable Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Set a keep-alive duration that determines how frequently to send
		 * heartbeats during periods of inactivity.
		 * @param keepAliveDuration how frequently to send empty comment messages
		 * when no other messages are sent
		 */
		public Builder keepAliveDuration(@Nullable Duration keepAliveDuration) {
			this.keepAliveDuration = keepAliveDuration;
			return this;
		}

		/**
		 * Set the HTTP methods that the handler should support.
		 * <p>By default, only {@link HttpMethod#POST} is supported. Enabling
		 * {@link HttpMethod#GET} allows clients such as the browser
		 * {@code EventSource} API, which can only issue GET requests, to use
		 * this endpoint.
		 * @param httpMethods the HTTP methods to support
		 */
		public Builder httpMethods(HttpMethod... httpMethods) {
			this.httpMethods = Set.of(httpMethods);
			return this;
		}

		/**
		 * Build the {@link GraphQlSseHandler}.
		 */
		public GraphQlSseHandler build() {
			return new GraphQlSseHandler(this.graphQlHandler, this.timeout, this.keepAliveDuration, this.httpMethods);
		}

	}

}

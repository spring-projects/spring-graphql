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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import jakarta.servlet.ServletException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
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
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public class GraphQlSseHandler extends AbstractGraphQlHttpHandler {

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();


	public GraphQlSseHandler(WebGraphQlHandler graphQlHandler) {
		super(graphQlHandler, null);
	}

	/**
	 * Handle GraphQL requests over HTTP using the Server-Sent Events protocol.
	 * @param request the incoming HTTP request
	 * @return the HTTP response
	 * @throws ServletException may be raised when reading the request body, e.g.
	 * {@link HttpMediaTypeNotSupportedException}.
	 */
	@SuppressWarnings("unchecked")
	public ServerResponse handleRequest(ServerRequest request) throws ServletException {

		WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
				request.uri(), request.headers().asHttpHeaders(), initCookies(request),
				request.remoteAddress().orElse(null), request.attributes(),
				readBody(request), this.idGenerator.generateId().toString(),
				LocaleContextHolder.getLocale());

		if (logger.isDebugEnabled()) {
			logger.debug("Executing: " + graphQlRequest);
		}

		Flux<Map<String, Object>> resultFlux = this.graphQlHandler.handleRequest(graphQlRequest)
				.flatMapMany((response) -> {
					if (logger.isDebugEnabled()) {
						List<ResponseError> errors = response.getErrors();
						logger.debug("Execution result " +
								(!CollectionUtils.isEmpty(errors) ? "has errors: " + errors : "is ready") + ".");
					}
					if (response.getData() instanceof Publisher) {
						return Flux.from((Publisher<ExecutionResult>) response.getData())
								.map(ExecutionResult::toSpecification);
					}
					else {
						if (logger.isDebugEnabled()) {
							logger.debug("A subscription DataFetcher must return a Publisher: " + response.getData());
						}
						return Flux.just(ExecutionResult.newExecutionResult()
								.addError(GraphQLError.newError()
										.errorType(ErrorType.OperationNotSupported)
										.message("SSE handler supports only subscriptions")
										.build())
								.build()
								.toSpecification());
					}
				});

		return ServerResponse.sse(SseSubscriber.connect(resultFlux));
	}


	/**
	 * {@link org.reactivestreams.Subscriber} that writes to {@link ServerResponse.SseBuilder}.
	 */
	private static final class SseSubscriber extends BaseSubscriber<Map<String, Object>> {

		private final ServerResponse.SseBuilder sseBuilder;

		private SseSubscriber(ServerResponse.SseBuilder sseBuilder) {
			this.sseBuilder = sseBuilder;
		}

		@Override
		protected void hookOnNext(Map<String, Object> value) {
			writeResult(value);
		}

		private void writeResult(Map<String, Object> value) {
			try {
				this.sseBuilder.event("next");
				this.sseBuilder.data(value);
			}
			catch (IOException exception) {
				onError(exception);
			}
		}

		@Override
		protected void hookOnError(Throwable ex) {
			if (ex instanceof SubscriptionPublisherException spe) {
				ExecutionResult result = ExecutionResult.newExecutionResult().errors(spe.getErrors()).build();
				writeResult(result.toSpecification());
			}
			else {
				this.sseBuilder.error(ex);
			}
			hookOnComplete();
		}

		@Override
		protected void hookOnComplete() {
			try {
				this.sseBuilder.event("complete").data("");
			}
			catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			this.sseBuilder.complete();
		}

		static Consumer<ServerResponse.SseBuilder> connect(Flux<Map<String, Object>> resultFlux) {
			return (sseBuilder) -> {
				SseSubscriber subscriber = new SseSubscriber(sseBuilder);
				resultFlux.subscribe(subscriber);
			};
		}
	}

}

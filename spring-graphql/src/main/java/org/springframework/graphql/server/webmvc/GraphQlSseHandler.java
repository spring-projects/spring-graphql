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
import java.util.Map;
import java.util.function.Consumer;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.reactivestreams.Publisher;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.SubscriptionPublisherException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;
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
				cancel();
				hookOnError(exception);
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

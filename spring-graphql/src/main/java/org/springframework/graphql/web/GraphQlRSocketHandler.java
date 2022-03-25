/*
 * Copyright 2020-2022 the original author or authors.
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

package org.springframework.graphql.web;


import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import io.rsocket.exceptions.RejectedException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.web.RSocketGraphQlHandlerInterceptor.Chain;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;


/**
 * Handler for GraphQL over RSocket requests.
 *
 * <p>This class can be extended or wrapped from an {@code @Controller} in order
 * to re-declare {@link #handle(Map)} and {@link #handleSubscription(Map)} with
 * {@link org.springframework.messaging.handler.annotation.MessageMapping @MessageMapping}
 * annotations including the GraphQL endpoint route.
 *
 * <pre style="class">
 * &#064;Controller
 * private static class GraphQlRSocketController {
 *
 *    private final GraphQlRSocketHandler handler;
 *
 *    GraphQlRSocketController(GraphQlRSocketHandler handler) {
 *        this.handler = handler;
 *    }
 *
 *    &#064;MessageMapping("graphql")
 *    public Mono<Map<String, Object>> handle(Map<String, Object> payload) {
 *        return this.handler.handle(payload);
 *    }
 *
 *    &#064;MessageMapping("graphql")
 *    public Flux<Map<String, Object>> handleSubscription(Map<String, Object> payload) {
 *        return this.handler.handleSubscription(payload);
 *    }
 * }
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlRSocketHandler {

	private final Chain executionChain;

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();


	/**
	 * Create a new instance that handles requests through a chain of interceptors
	 * followed by the given {@link ExecutionGraphQlService}.
	 */
	public GraphQlRSocketHandler(
			ExecutionGraphQlService service, List<RSocketGraphQlHandlerInterceptor> interceptors) {

		Chain endOfChain = request -> service.execute(request).map(RSocketGraphQlResponse::new);

		this.executionChain = (interceptors.isEmpty() ? endOfChain :
				interceptors.stream()
						.reduce(RSocketGraphQlHandlerInterceptor::andThen)
						.map(interceptor -> (Chain) request -> interceptor.intercept(request, endOfChain))
						.orElse(endOfChain));
	}


	/**
	 * Handle a {@code Request-Response} interaction. For queries and mutations.
	 */
	public Mono<Map<String, Object>> handle(Map<String, Object> payload) {
		return handleInternal(payload).map(ExecutionGraphQlResponse::toMap);
	}

	/**
	 * Handle a {@code Request-Stream} interaction. For subscriptions.
	 */
	public Flux<Map<String, Object>> handleSubscription(Map<String, Object> payload) {
		return handleInternal(payload)
				.flatMapMany(response -> {
					if (response.getData() instanceof Publisher) {
						Publisher<ExecutionResult> publisher = response.getData();
						return Flux.from(publisher).map(ExecutionResult::toSpecification);
					}

					String message = (!response.isValid() ?
							response.toMap().get("errors").toString() :
							"Response is not a stream, is the operation actually a subscription?");

					return Flux.error(new RejectedException(message));
				});
	}

	private Mono<RSocketGraphQlResponse> handleInternal(Map<String, Object> payload) {
		String requestId = this.idGenerator.generateId().toString();
		return this.executionChain.next(new RSocketGraphQlRequest(payload, requestId, null));
	}

}

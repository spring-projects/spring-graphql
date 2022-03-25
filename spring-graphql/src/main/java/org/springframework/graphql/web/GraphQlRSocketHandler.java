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


import java.util.Map;

import graphql.ExecutionResult;
import io.rsocket.exceptions.RejectedException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;


/**
 * Handler for GraphQL over RSocket requests.
 *
 * <p>This class can be extended from an {@code @Controller} that overrides
 * {@link #handle(Map)} and {@link #handleSubscription(Map)} in order to add
 * {@link org.springframework.messaging.handler.annotation.MessageMapping @MessageMapping}
 * annotations with the route.
 *
 * <pre style="class">
 * &#064;Controller
 * private static class GraphQlRSocketController extends GraphQlRSocketHandler {
 *
 *    GraphQlRSocketController(ExecutionGraphQlService graphQlService) {
 *        super(graphQlService);
 *    }
 *
 *    &#064;Override
 *    &#064;MessageMapping("graphql")
 *    public Mono<Map<String, Object>> handle(Map<String, Object> payload) {
 *        return super.handle(payload);
 *    }
 *
 *    &#064;Override
 *    &#064;MessageMapping("graphql")
 *    public Flux<Map<String, Object>> handleSubscription(Map<String, Object> payload) {
 *        return super.handleSubscription(payload);
 *    }
 * }
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlRSocketHandler {

	private final ExecutionGraphQlService service;


	public GraphQlRSocketHandler(ExecutionGraphQlService service) {
		this.service = service;
	}


	/**
	 * Handle a {@code Request-Response} interaction. For queries and mutations.
	 */
	public Mono<Map<String, Object>> handle(Map<String, Object> payload) {
		return this.service.execute(initRequest(payload)).map(ExecutionGraphQlResponse::toMap);
	}

	/**
	 * Handle a {@code Request-Stream} interaction. For subscriptions.
	 */
	public Flux<Map<String, Object>> handleSubscription(Map<String, Object> payload) {
		return this.service.execute(initRequest(payload))
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

	@SuppressWarnings("unchecked")
	private ExecutionGraphQlRequest initRequest(Map<String, Object> payload) {
		String query = (String) payload.get("query");
		String operationName = (String) payload.get("operationName");
		Map<String, Object> variables = (Map<String, Object>) payload.get("variables");
		return new DefaultExecutionGraphQlRequest(query, operationName, variables, "1", null);
	}

}

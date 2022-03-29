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

package org.springframework.graphql.server;


import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.exceptions.RejectedException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.server.RSocketGraphQlInterceptor.Chain;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.IdGenerator;
import org.springframework.util.MimeTypeUtils;


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

	private static final ResolvableType LIST_TYPE = ResolvableType.forClass(List.class);


	private final Chain executionChain;

	private final Encoder<?> jsonEncoder;

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();


	/**
	 * Create a new instance that handles requests through a chain of interceptors
	 * followed by the given {@link ExecutionGraphQlService}.
	 * @param graphQlService the service that will execute the request
	 * @param interceptors interceptors to form the processing chain
	 * @param jsonEncoder a JSON encoder for serializing a
	 * {@link graphql.GraphQLError} list for a failed subscription
	 */
	public GraphQlRSocketHandler(
			ExecutionGraphQlService graphQlService, List<RSocketGraphQlInterceptor> interceptors,
			Encoder<?> jsonEncoder) {

		Assert.notNull(graphQlService, "ExecutionGraphQlService is required");
		Assert.notNull(jsonEncoder, "JSON Encoder is required");

		this.executionChain = initChain(graphQlService, interceptors);
		this.jsonEncoder = jsonEncoder;
	}

	private static Chain initChain(ExecutionGraphQlService service, List<RSocketGraphQlInterceptor> interceptors) {
		Chain endOfChain = request -> service.execute(request).map(RSocketGraphQlResponse::new);
		return interceptors.isEmpty() ? endOfChain :
				interceptors.stream()
						.reduce(RSocketGraphQlInterceptor::andThen)
						.map(interceptor -> interceptor.apply(endOfChain))
						.orElse(endOfChain);
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
					else if (response.isValid()) {
						return Flux.error(new InvalidException(
								"Expected a Publisher for a subscription operation. " +
										"This is either a server error or the operation is not a subscription"));
					}
					String errorData = encodeErrors(response).toString(StandardCharsets.UTF_8);
					return Flux.error(new RejectedException(errorData));
				});
	}

	private Mono<RSocketGraphQlResponse> handleInternal(Map<String, Object> payload) {
		String requestId = this.idGenerator.generateId().toString();
		return this.executionChain.next(new RSocketGraphQlRequest(payload, requestId, null));
	}

	@SuppressWarnings("unchecked")
	private DataBuffer encodeErrors(RSocketGraphQlResponse response) {
		return ((Encoder<List<GraphQLError>>) this.jsonEncoder).encodeValue(
				response.getExecutionResult().getErrors(),
				DefaultDataBufferFactory.sharedInstance, LIST_TYPE, MimeTypeUtils.APPLICATION_JSON, null);
	}

}

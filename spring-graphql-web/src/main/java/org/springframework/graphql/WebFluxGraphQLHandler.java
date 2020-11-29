/*
 * Copyright 2020-2020 the original author or authors.
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
package org.springframework.graphql;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.GraphQL;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * GraphQL handler to expose as a WebFlux.fn endpoint via
 * {@link org.springframework.web.reactive.function.server.RouterFunctions}.
 */
public class WebFluxGraphQLHandler {

	private final WebInterceptorExecutionChain executionChain;

	private final Decoder<?> jsonDecoder;

	private final Encoder<?> jsonEncoder;


	/**
	 * Create a handler that executes queries through the given {@link GraphQL}
	 * and and invokes the given interceptors to customize input to and the
	 * result from the execution of the query.
	 * @param graphQL the GraphQL instance to use for query execution
	 * @param interceptors 0 or more interceptors to customize input and output
	 * @param jsonDecoder to decode JSON for subscriptions over WebSocket
	 * @param jsonEncoder to encode JSON for subscriptions over WebSocket
	 */
	public WebFluxGraphQLHandler(GraphQL graphQL, List<WebInterceptor> interceptors,
			Decoder<?> jsonDecoder, Encoder<?> jsonEncoder) {

		this.executionChain = new WebInterceptorExecutionChain(graphQL, interceptors);
		this.jsonDecoder = jsonDecoder;
		this.jsonEncoder = jsonEncoder;
	}


	/**
	 * Handle GraphQL query requests over HTTP.
	 */
	public Mono<ServerResponse> handleQuery(ServerRequest request) {
		return request.bodyToMono(WebInput.MAP_PARAMETERIZED_TYPE_REF)
				.flatMap(body -> {
					WebInput webInput = new WebInput(request.uri(), request.headers().asHttpHeaders(), body);
					return this.executionChain.execute(webInput);
				})
				.flatMap(output -> {
					ServerResponse.BodyBuilder builder = ServerResponse.ok();
					if (output.getHeaders() != null) {
						builder.headers(headers -> headers.putAll(output.getHeaders()));
					}
					return builder.bodyValue(output.toSpecification());
				});
	}

	/**
	 * Return a handler that supports subscriptions over WebSocket.
	 */
	public WebSocketHandler getSubscriptionWebSocketHandler() {
		return new SubscriptionWebSocketHandler();
	}


	/**
	 * Handler for subscriptions over WebSocket.
	 */
	private class SubscriptionWebSocketHandler implements WebSocketHandler {

		@Override
		@SuppressWarnings("unchecked")
		public Mono<Void> handle(WebSocketSession session) {
			return session.send(session.receive()
					.concatMap(message -> {
						Map<String, Object> map = decode(message);
						HandshakeInfo handshakeInfo = session.getHandshakeInfo();
						WebInput webInput = new WebInput(handshakeInfo.getUri(), handshakeInfo.getHeaders(), map);
						return executionChain.execute(webInput);
					})
					.concatMap(output -> {
						if (!CollectionUtils.isEmpty(output.getErrors())) {
							throw new IllegalStateException(
									"Execution failed: " + output.getErrors());
						}
						if (!(output.getData() instanceof Publisher)) {
							throw new IllegalStateException(
									"Expected Publisher<ExecutionResult>: " + output.toSpecification());
						}
						return (Publisher<ExecutionResult>) output.getData();
					})
					.map(result -> encode(session, result.getData()))
			);
		}

		@SuppressWarnings({"unchecked", "ConstantConditions"})
		private Map<String, Object> decode(WebSocketMessage message) {
			DataBuffer buffer = message.getPayload();
			return (Map<String, Object>) jsonDecoder.decode(
					DataBufferUtils.retain(buffer), WebInput.MAP_RESOLVABLE_TYPE, null, Collections.emptyMap());
		}

		@SuppressWarnings("unchecked")
		private <T> WebSocketMessage encode(WebSocketSession session, Object data) {
			DataBuffer buffer = ((Encoder<T>) jsonEncoder).encodeValue((T) data,
					session.bufferFactory(),
					ResolvableType.forInstance(data),
					MimeTypeUtils.APPLICATION_JSON,
					Collections.emptyMap());
			return new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);
		}
	}

}

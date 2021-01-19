/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.webflux;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphqlErrorBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.graphql.WebInterceptor;
import org.springframework.graphql.WebInterceptorExecutionChain;
import org.springframework.graphql.WebOutput;
import org.springframework.graphql.WebSocketMessageInput;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a>
 * and for use in a Spring WebFlux application.
 */
public class GraphQLWebSocketHandler implements WebSocketHandler {

	private static final Log logger = LogFactory.getLog(GraphQLWebSocketHandler.class);

	private static final List<String> SUB_PROTOCOL_LIST = Collections.singletonList("graphql-transport-ws");

	static final ResolvableType MAP_RESOLVABLE_TYPE =
			ResolvableType.forType(new ParameterizedTypeReference<Map<String, Object>>() {});


	private final WebInterceptorExecutionChain executionChain;

	private final Decoder<?> decoder;

	private final Encoder<?> encoder;

	private final Duration initTimeoutDuration;


	/**
	 * Create a new instance.
	 * @param graphQL the GraphQL instance to use for query execution
	 * @param interceptors 0 or more interceptors to customize input and output
	 * @param configurer codec configurer for JSON encoding and decoding
	 * @param initTimeoutDuration the time within which the {@code CONNECTION_INIT}
	 * type message must be received.
	 */
	public GraphQLWebSocketHandler(GraphQL graphQL, List<WebInterceptor> interceptors,
			ServerCodecConfigurer configurer, Duration initTimeoutDuration) {

		this.executionChain = new WebInterceptorExecutionChain(graphQL, interceptors);
		this.decoder = initDecoder(configurer);
		this.encoder = initEncoder(configurer);
		this.initTimeoutDuration = initTimeoutDuration;
	}

	private static Decoder<?> initDecoder(ServerCodecConfigurer configurer) {
		return configurer.getReaders().stream()
				.filter(reader -> reader.canRead(MAP_RESOLVABLE_TYPE, MediaType.APPLICATION_JSON))
				.map(reader -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Decoder"));
	}

	private static Encoder<?> initEncoder(ServerCodecConfigurer configurer) {
		return configurer.getWriters().stream()
				.filter(writer -> writer.canWrite(MAP_RESOLVABLE_TYPE, MediaType.APPLICATION_JSON))
				.map(writer -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Encoder"));
	}


	@Override
	public List<String> getSubProtocols() {
		return SUB_PROTOCOL_LIST;
	}


	@Override
	public Mono<Void> handle(WebSocketSession session) {
		// Session state
		AtomicBoolean connectionInitProcessed = new AtomicBoolean();
		Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

		Mono.delay(this.initTimeoutDuration)
				.then(Mono.defer(() -> connectionInitProcessed.compareAndSet(false, true) ?
						session.close(GraphQLStatus.INIT_TIMEOUT_STATUS) :
						Mono.empty()))
				.subscribe();

		Flux<WebSocketMessage> responseFlux = session.receive()
				.flatMap(message -> {
					Map<String, Object> map = decode(message);
					String id = (String) map.get("id");
					MessageType messageType = MessageType.resolve((String) map.get("type"));
					if (messageType == null) {
						return GraphQLStatus.close(session, GraphQLStatus.INVALID_MESSAGE_STATUS);
					}
					switch (messageType) {
						case SUBSCRIBE:
							if (!connectionInitProcessed.get()) {
								return GraphQLStatus.close(session, GraphQLStatus.UNAUTHORIZED_STATUS);
							}
							if (id == null) {
								return GraphQLStatus.close(session, GraphQLStatus.INVALID_MESSAGE_STATUS);
							}
							HandshakeInfo info = session.getHandshakeInfo();
							WebSocketMessageInput input = new WebSocketMessageInput(info.getUri(), info.getHeaders(), id, getPayload(map));
							if (logger.isDebugEnabled()) {
								logger.debug("Executing: " + input);
							}
							return executionChain.execute(input).flatMapMany(output ->
									handleWebOutput(session, input.requestId(), subscriptions, output));
						case COMPLETE:
							if (id != null) {
								Subscription subscription = subscriptions.remove(id);
								if (subscription != null) {
									subscription.cancel();
								}
							}
							return Flux.empty();
						case CONNECTION_INIT:
							if (!connectionInitProcessed.compareAndSet(false, true)) {
								return GraphQLStatus.close(session, GraphQLStatus.TOO_MANY_INIT_REQUESTS_STATUS);
							}
							return Flux.just(encode(session, null, MessageType.CONNECTION_ACK, null));
						default:
							return GraphQLStatus.close(session, GraphQLStatus.INVALID_MESSAGE_STATUS);
					}
				});

		return session.send(responseFlux);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> decode(WebSocketMessage message) {
		DataBuffer buffer = DataBufferUtils.retain(message.getPayload());
		return (Map<String, Object>) decoder.decode(buffer, MAP_RESOLVABLE_TYPE, null, null);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> getPayload(Map<String, Object> message) {
		Map<String, Object> payload = (Map<String, Object>) message.get("payload");
		Assert.notNull(payload, "No \"payload\" in message: " + message);
		return payload;
	}

	@SuppressWarnings("unchecked")
	private Flux<WebSocketMessage> handleWebOutput(
			WebSocketSession session, String id, Map<String, Subscription> subscriptions, WebOutput output) {

		if (logger.isDebugEnabled()) {
			logger.debug("Execution result ready" +
					(!CollectionUtils.isEmpty(output.getErrors()) ?
							" with errors: " + output.getErrors() : "") + ".");
		}

		Flux<ExecutionResult> outputFlux;
		if (output.getData() instanceof Publisher) {
			// Subscription
			outputFlux = Flux.from((Publisher<ExecutionResult>) output.getData())
					.doOnSubscribe(subscription -> {
						Subscription previous = subscriptions.putIfAbsent(id, subscription);
						if (previous != null) {
							throw new SubscriptionExistsException();
						}
					});
		}
		else {
			// Query
			outputFlux = (CollectionUtils.isEmpty(output.getErrors()) ?
					Flux.just(output) :
					Flux.error(new IllegalStateException("Execution failed: " + output.getErrors())));
		}

		return outputFlux
				.map(result -> {
					Map<String, Object> dataMap = result.toSpecification();
					return encode(session, id, MessageType.NEXT, dataMap);
				})
				.concatWith(Mono.defer(() -> Mono.just(encode(session, id, MessageType.COMPLETE, null))))
				.onErrorResume(ex -> {
					if (ex instanceof SubscriptionExistsException) {
						return GraphQLStatus.close(session,
								new CloseStatus(4409, "Subscriber for " + id + " already exists"));
					}
					ErrorType errorType = ErrorType.DataFetchingException;
					String message = ex.getMessage();
					Map<String, Object> errorMap = GraphqlErrorBuilder.newError()
							.errorType(errorType)
							.message(message)
							.build()
							.toSpecification();
					return Mono.just(encode(session, id, MessageType.ERROR, errorMap));
				});
	}

	@SuppressWarnings("unchecked")
	private <T> WebSocketMessage encode(
			WebSocketSession session, @Nullable String id, MessageType messageType, @Nullable Object payload) {

		Map<String, Object> payloadMap = new HashMap<>(3);
		if (id != null) {
			payloadMap.put("id", id);
		}
		payloadMap.put("type", messageType.getType());
		if (payload != null) {
			payloadMap.put("payload", payload);
		}

		DataBuffer buffer = ((Encoder<T>) encoder).encodeValue(
				(T) payloadMap, session.bufferFactory(), MAP_RESOLVABLE_TYPE,
				MimeTypeUtils.APPLICATION_JSON, null);

		return new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);
	}


	private enum MessageType {

		CONNECTION_INIT("connection_init"),
		CONNECTION_ACK("connection_ack"),
		SUBSCRIBE("subscribe"),
		NEXT("next"),
		ERROR("error"),
		COMPLETE("complete");


		private static final Map<String, MessageType> messageTypes = new HashMap<>(6);

		static {
			for (MessageType messageType : MessageType.values()) {
				messageTypes.put(messageType.getType(), messageType);
			}
		}


		private final String type;

		MessageType(String type) {
			this.type = type;
		}

		public String getType() {
			return this.type;
		}

		@Nullable
		public static MessageType resolve(@Nullable String type) {
			return (type != null ? messageTypes.get(type) : null);
		}
	}


	private static class GraphQLStatus {

		static final CloseStatus INVALID_MESSAGE_STATUS = new CloseStatus(4400, "Invalid message");

		static final CloseStatus UNAUTHORIZED_STATUS = new CloseStatus(4401, "Unauthorized");

		static final CloseStatus INIT_TIMEOUT_STATUS = new CloseStatus(4408, "Connection initialisation timeout");

		static final CloseStatus TOO_MANY_INIT_REQUESTS_STATUS = new CloseStatus(4429, "Too many initialisation requests");


		static <V> Flux<V> close(WebSocketSession session, CloseStatus status) {
			return session.close(status).thenMany(Mono.empty());
		}
	}


	private static class SubscriptionExistsException extends RuntimeException {
	}

}

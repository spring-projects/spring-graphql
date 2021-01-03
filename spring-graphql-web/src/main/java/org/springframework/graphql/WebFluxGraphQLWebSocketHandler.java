/*
 * Copyright 2002-2020 the original author or authors.
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * WebSocketHandler for GraphQL based on
 * <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL Over WebSocket Protocol</a>
 */
public class WebFluxGraphQLWebSocketHandler implements WebSocketHandler {

	private static final Log logger = LogFactory.getLog(WebFluxGraphQLWebSocketHandler.class);

	private static final ResolvableType MAP_TYPE = ResolvableType.forClass(Map.class);


	private final WebInterceptorExecutionChain executionChain;

	private final Decoder<?> jsonDecoder;

	private final Encoder<?> jsonEncoder;


	/**
	 * Create a new instance.
	 * @param graphQL the GraphQL instance to use for query execution
	 * @param interceptors 0 or more interceptors to customize input and output
	 * @param configurer codec configurer for JSON encoding and decoding
	 */
	public WebFluxGraphQLWebSocketHandler(GraphQL graphQL, List<WebInterceptor> interceptors,
			ServerCodecConfigurer configurer) {

		this.executionChain = new WebInterceptorExecutionChain(graphQL, interceptors);
		this.jsonDecoder = initDecoder(configurer);
		this.jsonEncoder = initEncoder(configurer);
	}

	private static Decoder<?> initDecoder(ServerCodecConfigurer configurer) {
		return configurer.getReaders().stream()
				.filter(reader -> reader.canRead(MAP_TYPE, MediaType.APPLICATION_JSON))
				.map(reader -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Decoder"));
	}

	private static Encoder<?> initEncoder(ServerCodecConfigurer configurer) {
		return configurer.getWriters().stream()
				.filter(writer -> writer.canWrite(MAP_TYPE, MediaType.APPLICATION_JSON))
				.map(writer -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Encoder"));
	}


	@Override
	@SuppressWarnings("unchecked")
	public Mono<Void> handle(WebSocketSession session) {
		return session.send(session.receive()
				.concatMap(message -> {
					Map<String, Object> map = decode(message);
					HandshakeInfo handshakeInfo = session.getHandshakeInfo();
					WebInput webInput = new WebInput(handshakeInfo.getUri(), handshakeInfo.getHeaders(), map);
					if (logger.isDebugEnabled()) {
						logger.debug("Executing: " + webInput);
					}
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
					if (logger.isDebugEnabled()) {
						logger.debug("Execution complete, subscribing for events.");
					}
					return (Publisher<ExecutionResult>) output.getData();
				})
				.map(result -> {
					Object data = result.getData();
					return encode(session, data);
				})
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

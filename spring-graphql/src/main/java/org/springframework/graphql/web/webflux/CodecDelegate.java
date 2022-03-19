/*
 * Copyright 2002-2022 the original author or authors.
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
package org.springframework.graphql.web.webflux;

import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.graphql.web.support.GraphQlMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * Delegate that can be embedded in a class to help with encoding and decoding
 * GraphQL over WebSocket messages.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class CodecDelegate {

	private static final ResolvableType MESSAGE_TYPE = ResolvableType.forClass(GraphQlMessage.class);


	private final Decoder<?> decoder;

	private final Encoder<?> encoder;


	CodecDelegate(CodecConfigurer codecConfigurer) {
		Assert.notNull(codecConfigurer, "CodecConfigurer is required");
		this.decoder = findJsonDecoder(codecConfigurer);
		this.encoder = findJsonEncoder(codecConfigurer);
	}

	private static Decoder<?> findJsonDecoder(CodecConfigurer configurer) {
		return configurer.getReaders().stream()
				.filter((reader) -> reader.canRead(MESSAGE_TYPE, MediaType.APPLICATION_JSON))
				.map((reader) -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Decoder"));
	}

	private static Encoder<?> findJsonEncoder(CodecConfigurer configurer) {
		return configurer.getWriters().stream()
				.filter((writer) -> writer.canWrite(MESSAGE_TYPE, MediaType.APPLICATION_JSON))
				.map((writer) -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Encoder"));
	}


	@SuppressWarnings("unchecked")
	public <T> WebSocketMessage encode(WebSocketSession session, GraphQlMessage message) {

		DataBuffer buffer = ((Encoder<T>) this.encoder).encodeValue(
				(T) message, session.bufferFactory(), MESSAGE_TYPE, MimeTypeUtils.APPLICATION_JSON, null);

		return new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);
	}

	@SuppressWarnings("ConstantConditions")
	public GraphQlMessage decode(WebSocketMessage webSocketMessage) {
		DataBuffer buffer = DataBufferUtils.retain(webSocketMessage.getPayload());
		return (GraphQlMessage) this.decoder.decode(buffer, MESSAGE_TYPE, null, null);
	}

	public WebSocketMessage encodeConnectionAck(WebSocketSession session, Object ackPayload) {
		return encode(session, GraphQlMessage.connectionAck(ackPayload));
	}

	public WebSocketMessage encodeNext(WebSocketSession session, String id, Map<String, Object> responseMap) {
		return encode(session, GraphQlMessage.next(id, responseMap));
	}

	public WebSocketMessage encodeError(WebSocketSession session, String id, Throwable ex) {
		GraphQLError error = GraphqlErrorBuilder.newError().message(ex.getMessage()).build();
		return encode(session, GraphQlMessage.error(id, error));
	}

	public WebSocketMessage encodeComplete(WebSocketSession session, String id) {
		return encode(session, GraphQlMessage.complete(id));
	}


}

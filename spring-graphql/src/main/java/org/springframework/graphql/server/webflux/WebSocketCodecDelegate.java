/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.webflux;

import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import org.jspecify.annotations.Nullable;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.graphql.server.support.GraphQlWebSocketMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * Helper class for encoding and decoding GraphQL messages in WebSocket transport.
 *
 * @author Rossen Stoyanchev
 */
final class WebSocketCodecDelegate {

	private static final ResolvableType MESSAGE_TYPE = ResolvableType.forClass(GraphQlWebSocketMessage.class);


	private final Decoder<?> decoder;

	private final Encoder<?> encoder;


	WebSocketCodecDelegate(CodecConfigurer codecConfigurer) {
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
	<T> WebSocketMessage encode(WebSocketSession session, GraphQlWebSocketMessage message) {

		DataBuffer buffer = ((Encoder<T>) this.encoder).encodeValue(
				(T) message, session.bufferFactory(), MESSAGE_TYPE, MimeTypeUtils.APPLICATION_JSON, null);

		return new WebSocketMessage(WebSocketMessage.Type.TEXT, buffer);
	}

	@SuppressWarnings("ConstantConditions")
	@Nullable GraphQlWebSocketMessage decode(WebSocketMessage webSocketMessage) {
		DataBuffer buffer = DataBufferUtils.retain(webSocketMessage.getPayload());
		return (GraphQlWebSocketMessage) this.decoder.decode(buffer, MESSAGE_TYPE, null, null);
	}

	WebSocketMessage encodeConnectionAck(WebSocketSession session, Object ackPayload) {
		return encode(session, GraphQlWebSocketMessage.connectionAck(ackPayload));
	}

	WebSocketMessage encodeNext(WebSocketSession session, String id, Map<String, Object> responseMap) {
		return encode(session, GraphQlWebSocketMessage.next(id, responseMap));
	}

	WebSocketMessage encodeError(WebSocketSession session, String id, List<GraphQLError> errors) {
		return encode(session, GraphQlWebSocketMessage.error(id, errors));
	}

	WebSocketMessage encodeComplete(WebSocketSession session, String id) {
		return encode(session, GraphQlWebSocketMessage.complete(id));
	}

}

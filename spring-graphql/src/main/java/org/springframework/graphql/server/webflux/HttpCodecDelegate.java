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
package org.springframework.graphql.server.webflux;

import java.util.Map;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;

/**
 * Helper class for encoding and decoding GraphQL messages in HTTP transport.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.3.0
 */
final class HttpCodecDelegate {

	private static final ResolvableType REQUEST_TYPE = ResolvableType.forClass(SerializableGraphQlRequest.class);

	private static final ResolvableType RESPONSE_TYPE = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);


	private final Decoder<?> decoder;

	private final Encoder<?> encoder;


	HttpCodecDelegate(CodecConfigurer codecConfigurer) {
		Assert.notNull(codecConfigurer, "CodecConfigurer is required");
		this.decoder = findJsonDecoder(codecConfigurer);
		this.encoder = findJsonEncoder(codecConfigurer);
	}

	private static Decoder<?> findJsonDecoder(CodecConfigurer configurer) {
		return configurer.getReaders().stream()
				.filter((reader) -> reader.canRead(REQUEST_TYPE, MediaType.APPLICATION_JSON))
				.map((reader) -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Decoder"));
	}

	private static Encoder<?> findJsonEncoder(CodecConfigurer configurer) {
		return configurer.getWriters().stream()
				.filter((writer) -> writer.canWrite(RESPONSE_TYPE, MediaType.APPLICATION_JSON))
				.map((writer) -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Encoder"));
	}


	@SuppressWarnings("unchecked")
	public DataBuffer encode(GraphQlResponse response) {
		return ((Encoder<Map<String, Object>>) this.encoder)
				.encodeValue(response.toMap(), DefaultDataBufferFactory.sharedInstance, RESPONSE_TYPE, MimeTypeUtils.APPLICATION_JSON, null);
	}

	@SuppressWarnings("unchecked")
	public Mono<SerializableGraphQlRequest> decode(Publisher<DataBuffer> inputStream, MediaType contentType) {
		return (Mono<SerializableGraphQlRequest>) this.decoder.decodeToMono(inputStream, REQUEST_TYPE, contentType, null);
	}

}

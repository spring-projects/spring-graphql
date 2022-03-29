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

package org.springframework.graphql.test.tester;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;


/**
 * JSON Path {@link MappingProvider} that uses {@link Encoder} and {@link Decoder}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class EncoderDecoderMappingProvider implements MappingProvider {

	private static final ResolvableType MAP_TYPE = ResolvableType.forClass(Map.class);


	private final Encoder<?> encoder;

	private final Decoder<?> decoder;


	/**
	 * Create an instance with a {@link CodecConfigurer}.
	 */
	public EncoderDecoderMappingProvider(CodecConfigurer configurer) {
		this.encoder = findJsonEncoder(configurer);
		this.decoder = findJsonDecoder(configurer);
	}

	/**
	 * Create an instance with a List of encoders and decoders>
	 */
	public EncoderDecoderMappingProvider(List<Encoder<?>> encoders, List<Decoder<?>> decoders) {
		this.encoder = findJsonEncoder(encoders);
		this.decoder = findJsonDecoder(decoders);
	}

	private static Encoder<?> findJsonEncoder(CodecConfigurer configurer) {
		return findJsonEncoder(configurer.getWriters().stream()
				.filter(writer -> writer instanceof EncoderHttpMessageWriter)
				.map(writer -> ((EncoderHttpMessageWriter<?>) writer).getEncoder()));
	}

	private static Decoder<?> findJsonDecoder(CodecConfigurer configurer) {
		return findJsonDecoder(configurer.getReaders().stream()
				.filter(reader -> reader instanceof DecoderHttpMessageReader)
				.map(reader -> ((DecoderHttpMessageReader<?>) reader).getDecoder()));
	}

	private static Encoder<?> findJsonEncoder(List<Encoder<?>> encoders) {
		return findJsonEncoder(encoders.stream());
	}

	private static Decoder<?> findJsonDecoder(List<Decoder<?>> decoders) {
		return findJsonDecoder(decoders.stream());
	}

	private static Encoder<?> findJsonEncoder(Stream<Encoder<?>> stream) {
		return stream
				.filter(encoder -> encoder.canEncode(MAP_TYPE, MediaType.APPLICATION_JSON))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Encoder"));
	}

	private static Decoder<?> findJsonDecoder(Stream<Decoder<?>> decoderStream) {
		return decoderStream
				.filter(decoder -> decoder.canDecode(MAP_TYPE, MediaType.APPLICATION_JSON))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Decoder"));
	}


	@Nullable
	@Override
	public <T> T map(Object source, Class<T> targetType, Configuration configuration) {
		return mapToTargetType(source, ResolvableType.forClass(targetType));
	}

	@Nullable
	@Override
	public <T> T map(Object source, TypeRef<T> targetType, Configuration configuration) {
		return mapToTargetType(source, ResolvableType.forType(targetType.getType()));
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private <T> T mapToTargetType(Object source, ResolvableType targetType) {

		DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
		MimeType mimeType = MimeTypeUtils.APPLICATION_JSON;
		Map<String, Object> hints = Collections.emptyMap();

		DataBuffer buffer = ((Encoder<T>) this.encoder).encodeValue(
				(T) source, bufferFactory, ResolvableType.forInstance(source), mimeType, hints);

		return ((Decoder<T>) this.decoder).decode(buffer, targetType, mimeType, hints);
	}

}

/*
 * Copyright 2020-2023 the original author or authors.
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

package org.springframework.graphql.data.query;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeTypeUtils;

/**
 * Strategy to convert a {@link KeysetScrollPosition#getKeys() keyset} to and
 * from a JSON String for use with {@link ScrollPositionCursorStrategy}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public final class JsonKeysetCursorStrategy implements CursorStrategy<Map<String, Object>> {

	private static final ResolvableType MAP_TYPE =
			ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);

	private static final boolean jackson2Present = ClassUtils.isPresent(
			"com.fasterxml.jackson.databind.ObjectMapper", JsonKeysetCursorStrategy.class.getClassLoader());


	private final Encoder<?> encoder;

	private final Decoder<?> decoder;

	private final DefaultDataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;


	/**
	 * Shortcut constructor that uses {@link ServerCodecConfigurer}.
	 */
	public JsonKeysetCursorStrategy() {
		this(initCodecConfigurer());
	}

	private static ServerCodecConfigurer initCodecConfigurer() {
		ServerCodecConfigurer configurer = ServerCodecConfigurer.create();
		if (jackson2Present) {
			JacksonObjectMapperCustomizer.customize(configurer);
		}
		return configurer;
	}

	/**
	 * Constructor with a {@link CodecConfigurer} in which to find the JSON
	 * encoder and decoder to use.
	 */
	public JsonKeysetCursorStrategy(CodecConfigurer codecConfigurer) {
		Assert.notNull(codecConfigurer, "CodecConfigurer is required");
		this.encoder = findJsonEncoder(codecConfigurer);
		this.decoder = findJsonDecoder(codecConfigurer);
	}

	private static Decoder<?> findJsonDecoder(CodecConfigurer configurer) {
		return configurer.getReaders().stream()
				.filter(reader -> reader.canRead(MAP_TYPE, MediaType.APPLICATION_JSON))
				.map(reader -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Decoder"));
	}

	private static Encoder<?> findJsonEncoder(CodecConfigurer configurer) {
		return configurer.getWriters().stream()
				.filter(writer -> writer.canWrite(MAP_TYPE, MediaType.APPLICATION_JSON))
				.map(writer -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Encoder"));
	}


	@Override
	public boolean supports(Class<?> targetType) {
		return Map.class.isAssignableFrom(targetType);
	}

	@SuppressWarnings("unchecked")
	@Override
	public String toCursor(Map<String, Object> keys) {
		return ((Encoder<Map<String, Object>>) this.encoder).encodeValue(
				keys, DefaultDataBufferFactory.sharedInstance, MAP_TYPE,
				MimeTypeUtils.APPLICATION_JSON, null).toString(StandardCharsets.UTF_8);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> fromCursor(String cursor) {
		DataBuffer buffer = this.bufferFactory.wrap(cursor.getBytes(StandardCharsets.UTF_8));
		Map<String, Object> map = ((Decoder<Map<String, Object>>) this.decoder).decode(buffer, MAP_TYPE, null, null);
		return map != null ? map : Collections.emptyMap();
	}


	/**
	 * Customizes the {@link ObjectMapper} to use default typing that supports
	 * {@link Date}, {@link Calendar}, and classes in {@code java.time}.
	 */
	private static class JacksonObjectMapperCustomizer {

		public static void customize(CodecConfigurer configurer) {

			PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
					.allowIfBaseType(Map.class)
					.allowIfSubType("java.time.")
					.allowIfSubType(Calendar.class)
					.allowIfSubType(Date.class)
					.build();

			ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
			mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL);

			configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
			configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
		}

	}

}

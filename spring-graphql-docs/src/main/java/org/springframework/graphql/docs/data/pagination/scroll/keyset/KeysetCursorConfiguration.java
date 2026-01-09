/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.graphql.docs.data.pagination.scroll.keyset;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.graphql.data.pagination.CursorEncoder;
import org.springframework.graphql.data.pagination.CursorStrategy;
import org.springframework.graphql.data.pagination.EncodingCursorStrategy;
import org.springframework.graphql.data.query.JsonKeysetCursorStrategy;
import org.springframework.graphql.data.query.ScrollPositionCursorStrategy;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;

@Configuration
public class KeysetCursorConfiguration {

	@Bean
	// override the EncodingCursorStrategy bean in Spring Boot
	public EncodingCursorStrategy<ScrollPosition> cursorStrategy() {
		JsonKeysetCursorStrategy keysetCursorStrategy = keysetCursorStrategy();
		ScrollPositionCursorStrategy cursorStrategy = new ScrollPositionCursorStrategy(keysetCursorStrategy);
		return CursorStrategy.withEncoder(cursorStrategy, CursorEncoder.base64());
	}

	// create a cursor strategy with a custom CodecConfigurer
	private JsonKeysetCursorStrategy keysetCursorStrategy() {
		JsonMapper mapper = keysetJsonMapper();
		CodecConfigurer codecConfigurer = keysetCodecConfigurer(mapper);
		return new JsonKeysetCursorStrategy(codecConfigurer);
	}

	// use a custom JsonMapper for encoding/decoding JSON
	private CodecConfigurer keysetCodecConfigurer(JsonMapper jsonMapper) {
		CodecConfigurer configurer = ServerCodecConfigurer.create();
		configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
		configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
		return configurer;
	}

	// create a custom JsonMapper
	private JsonMapper keysetJsonMapper() {
		// Configure which types should be allowed for serialization
		// those should include all fields included in the keyset
		PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
				.allowIfBaseType(Map.class)
				.allowIfSubType(Calendar.class)
				.allowIfSubType(Date.class)
				.allowIfSubType(UUID.class)
				.allowIfSubType(Number.class)
				.allowIfSubType(Enum.class)
				.allowIfSubType("java.time.")
				.build();

		return JsonMapper.builder()
				.activateDefaultTyping(validator, DefaultTyping.NON_FINAL)
				// as of Jackson 3.0, dates are not written as timestamps by default
				.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
	}

}

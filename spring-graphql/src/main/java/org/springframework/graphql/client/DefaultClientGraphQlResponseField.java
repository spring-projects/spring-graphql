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

package org.springframework.graphql.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.DefaultGraphQlResponseField;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;


/**
 * Default implementation of {@link ClientGraphQlResponseField}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultClientGraphQlResponseField extends DefaultGraphQlResponseField implements ClientGraphQlResponseField {


	DefaultClientGraphQlResponseField(DefaultClientGraphQlResponse response, String path) {
		super(response, path);
	}


	@Override
	public <D> D toEntity(Class<D> entityType) {
		return toEntity(ResolvableType.forType(entityType));
	}

	@Override
	public <D> D toEntity(ParameterizedTypeReference<D> entityType) {
		return toEntity(ResolvableType.forType(entityType));
	}

	@Override
	public <D> List<D> toEntityList(Class<D> elementType) {
		return toEntity(ResolvableType.forClassWithGenerics(List.class, elementType));
	}

	@Override
	public <D> List<D> toEntityList(ParameterizedTypeReference<D> elementType) {
		return toEntity(ResolvableType.forClassWithGenerics(List.class, ResolvableType.forType(elementType)));
	}

	@SuppressWarnings({"unchecked", "ConstantConditions"})
	private <T> T toEntity(ResolvableType targetType) {
		DefaultClientGraphQlResponse response = getResponse();
		if (!hasValue()) {
			throw new FieldAccessException(response, this);
		}

		DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
		MimeType mimeType = MimeTypeUtils.APPLICATION_JSON;
		Map<String, Object> hints = Collections.emptyMap();

		DataBuffer buffer = ((Encoder<T>) response.getEncoder()).encodeValue(
				(T) getValue(), bufferFactory, ResolvableType.forInstance(getValue()), mimeType, hints);

		return ((Decoder<T>) response.getDecoder()).decode(buffer, targetType, mimeType, hints);
	}

}

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
import org.springframework.graphql.GraphQlResponseError;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;


/**
 * Default implementation of {@link GraphQlResponseField}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultGraphQlResponseField implements GraphQlResponseField {

	private final DefaultClientGraphQlResponse response;

	private final String path;

	private final List<Object> parsedPath;

	@Nullable
	private final Object value;

	private final List<GraphQlResponseError> fieldErrors;


	DefaultGraphQlResponseField(
			DefaultClientGraphQlResponse response, String path, List<Object> parsedPath,
			@Nullable Object value, List<GraphQlResponseError> errors) {

		this.response = response;
		this.path = path;
		this.parsedPath = parsedPath;
		this.value = value;
		this.fieldErrors = errors;
	}


	@Override
	public String getPath() {
		return this.path;
	}

	@Override
	public List<Object> getParsedPath() {
		return this.parsedPath;
	}

	@Override
	public boolean hasValue() {
		return (this.value != null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getValue() {
		return (T) this.value;
	}

	@Override
	public GraphQlResponseError getError() {
		if (!hasValue()) {
			if (!this.fieldErrors.isEmpty()) {
				return this.fieldErrors.get(0);
			}
			if (!this.response.getErrors().isEmpty()) {
				return this.response.getErrors().get(0);
			}
			// No errors, set to null by DataFetcher
		}
		return null;
	}

	@Override
	public List<GraphQlResponseError> getErrors() {
		return this.fieldErrors;
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
		if (this.value == null) {
			throw new FieldAccessException(this.response, this);
		}

		DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
		MimeType mimeType = MimeTypeUtils.APPLICATION_JSON;
		Map<String, Object> hints = Collections.emptyMap();

		DataBuffer buffer = ((Encoder<T>) this.response.getEncoder()).encodeValue(
				(T) this.value, bufferFactory, ResolvableType.forInstance(this.value), mimeType, hints);

		return ((Decoder<T>) this.response.getDecoder()).decode(buffer, targetType, mimeType, hints);
	}

}

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

import graphql.GraphQLError;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.support.MapGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;


/**
 * Default implementation of {@link ClientGraphQlResponse}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class DefaultClientGraphQlResponse extends MapGraphQlResponse implements ClientGraphQlResponse {

	private final GraphQlRequest request;

	private final Encoder<?> encoder;

	private final Decoder<?> decoder;


	DefaultClientGraphQlResponse(
			GraphQlRequest request, GraphQlResponse response, Encoder<?> encoder, Decoder<?> decoder) {

		super(response.toMap());

		this.request = request;
		this.encoder = encoder;
		this.decoder = decoder;
	}


	@Override
	public GraphQlRequest getRequest() {
		return this.request;
	}

	@Override
	public ResponseField field(String path) {

		List<Object> dataPath = parseFieldPath(path);
		Object value = getFieldValue(dataPath);
		List<GraphQLError> errors = getFieldErrors(dataPath);

		return new DefaultField(
				path, dataPath, (value != NO_VALUE), (value != NO_VALUE ? value : null), errors);
	}

	@Override
	public <D> D toEntity(Class<D> type) {
		return field("").toEntity(type);
	}

	@Override
	public <D> D toEntity(ParameterizedTypeReference<D> type) {
		return field("").toEntity(type);
	}


	/**
	 * Default implementation of {@link ResponseField}.
	 */
	private class DefaultField implements ResponseField {

		private final String path;

		private final List<Object> parsedPath;

		private final List<GraphQLError> errors;

		private final boolean exists;

		@Nullable
		private final Object value;

		public DefaultField(
				String path, List<Object> parsedPath, boolean exists, @Nullable Object value,
				List<GraphQLError> errors) {

			this.path = path;
			this.parsedPath = parsedPath;
			this.exists = exists;
			this.value = value;
			this.errors = errors;
		}

		@Override
		public String getPath() {
			return this.path;
		}

		@Override
		public boolean isValid() {
			return (this.exists && (this.value != null || this.errors.isEmpty()));
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getValue() {
			return (T) this.value;
		}

		@Override
		public GraphQLError getError() {
			for (GraphQLError error : this.errors) {
				if (error.getPath().size() <= this.parsedPath.size()) {
					return error;
				}
			}
			return null;
		}

		@Override
		public List<GraphQLError> getErrors() {
			return this.errors;
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
			List<D> list = toEntity(ResolvableType.forClassWithGenerics(List.class, elementType));
			return (list != null ? list : Collections.emptyList());
		}

		@Override
		public <D> List<D> toEntityList(ParameterizedTypeReference<D> elementType) {
			List<D> list = toEntity(ResolvableType.forClassWithGenerics(List.class, ResolvableType.forType(elementType)));
			return (list != null ? list : Collections.emptyList());
		}

		@SuppressWarnings("unchecked")
		@Nullable
		private <T> T toEntity(ResolvableType targetType) {
			if (!isValid()) {
				throw new FieldAccessException(request, DefaultClientGraphQlResponse.this, this);
			}

			if (this.value == null) {
				return null;
			}

			DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
			MimeType mimeType = MimeTypeUtils.APPLICATION_JSON;
			Map<String, Object> hints = Collections.emptyMap();

			DataBuffer buffer = ((Encoder<T>) encoder).encodeValue(
					(T) this.value, bufferFactory, ResolvableType.forInstance(this.value), mimeType, hints);

			return ((Decoder<T>) decoder).decode(buffer, targetType, mimeType, hints);
		}

	}

}

/*
 * Copyright 2002-present the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.MimeType;


/**
 * {@link DefaultClientGraphQlResponse} uses {@link Encoder} and {@link Decoder}
 * to encode the response map to JSON and then encode it into higher level
 * objects. This delegate helps with finding an {@link HttpMessageConverter}
 * for JSON and adapt it to {@link Encoder} and {@link Decoder}.
 *
 * @author Rossen Stoyanchev
 */
final class HttpMessageConverterDelegate {

	private HttpMessageConverterDelegate() {

	}

	static boolean isJsonConverter(HttpMessageConverter<?> converter) {
		return converter.canRead(Map.class, MediaType.APPLICATION_JSON);
	}

	static HttpMessageConverterEncoder asEncoder(HttpMessageConverter<Object> converter) {
		return new HttpMessageConverterEncoder(converter);
	}

	static HttpMessageConverterDecoder asDecoder(HttpMessageConverter<Object> converter) {
		return new HttpMessageConverterDecoder(converter);
	}

	private static @Nullable MediaType toMediaType(@Nullable MimeType mimeType) {
		if (mimeType instanceof MediaType mediaType) {
			return mediaType;
		}
		return (mimeType != null) ? new MediaType(mimeType) : null;
	}


	/**
	 * Partial Encoder implementation to encode a single value through an HttpMessageConverter.
	 */
	private static final class HttpMessageConverterEncoder implements Encoder<Object> {

		private final HttpMessageConverter<Object> converter;

		private final List<MimeType> mimeTypes;

		private HttpMessageConverterEncoder(HttpMessageConverter<Object> converter) {
			this.converter = converter;
			this.mimeTypes = new ArrayList<>(this.converter.getSupportedMediaTypes());
		}

		@Override
		public List<MimeType> getEncodableMimeTypes() {
			return this.mimeTypes;
		}

		@Override
		public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
			return this.converter.canWrite(elementType.resolve(Object.class), toMediaType(mimeType));
		}

		@Override
		public DataBuffer encodeValue(
				Object value, DataBufferFactory bufferFactory, ResolvableType valueType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

			HttpOutputMessageAdapter messageAdapter = new HttpOutputMessageAdapter();
			try {
				if (this.converter instanceof GenericHttpMessageConverter<Object> genericConverter) {
					genericConverter.write(value, valueType.getType(), toMediaType(mimeType), messageAdapter);
				}
				else {
					this.converter.write(value, toMediaType(mimeType), messageAdapter);
				}
				return bufferFactory.wrap(messageAdapter.toByteArray());
			}
			catch (IOException ex) {
				// TODO: revisit
				throw new EncodingException("Error while encoding: " + ex.getMessage(), ex);
			}
		}

		@Override
		public Flux<DataBuffer> encode(
				Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

			throw new UnsupportedOperationException();
		}
	}


	/**
	 * Partial Decoder implementation to decode a single buffer through an HttpMessageConverter.
	 */
	private static final class HttpMessageConverterDecoder implements Decoder<Object> {

		private final HttpMessageConverter<Object> converter;

		private final List<MimeType> mimeTypes;

		private HttpMessageConverterDecoder(HttpMessageConverter<Object> converter) {
			this.converter = converter;
			this.mimeTypes = new ArrayList<>(this.converter.getSupportedMediaTypes());
		}

		@Override
		public List<MimeType> getDecodableMimeTypes() {
			return this.mimeTypes;
		}

		@Override
		public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
			return this.converter.canRead(elementType.resolve(Object.class), toMediaType(mimeType));
		}

		@Override
		public Object decode(DataBuffer buffer, ResolvableType targetType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {

			try {
				HttpInputMessageAdapter messageAdapter = new HttpInputMessageAdapter(buffer);
				if (this.converter instanceof GenericHttpMessageConverter<Object> genericConverter) {
					return genericConverter.read(targetType.getType(), null, messageAdapter);
				}
				else {
					return this.converter.read(targetType.resolve(Object.class), messageAdapter);
				}
			}
			catch (IOException ex) {
				throw new DecodingException(ex.getMessage(), ex);
			}
		}

		@Override
		public Mono<Object> decodeToMono(Publisher<DataBuffer> inputStream, ResolvableType elementType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

			throw new UnsupportedOperationException();
		}

		@Override
		public Flux<Object> decode(
				Publisher<DataBuffer> inputStream, ResolvableType elementType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

			throw new UnsupportedOperationException();
		}
	}


	private static final class HttpInputMessageAdapter extends ByteArrayInputStream implements HttpInputMessage {

		HttpInputMessageAdapter(DataBuffer buffer) {
			super(toBytes(buffer));
		}

		private static byte[] toBytes(DataBuffer buffer) {
			byte[] bytes = new byte[buffer.readableByteCount()];
			buffer.read(bytes);
			DataBufferUtils.release(buffer);
			return bytes;
		}

		@Override
		public InputStream getBody() {
			return this;
		}

		@Override
		public HttpHeaders getHeaders() {
			return HttpHeaders.EMPTY;
		}

	}


	private static final class HttpOutputMessageAdapter extends ByteArrayOutputStream implements HttpOutputMessage {

		private static final HttpHeaders noOpHeaders = new HttpHeaders();

		@Override
		public OutputStream getBody() {
			return this;
		}

		@Override
		public HttpHeaders getHeaders() {
			return noOpHeaders;
		}

	}

}

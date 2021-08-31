/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ValueConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * Resolver for {@link Argument @Argument} annotated method parameters, obtained
 * via {@link DataFetchingEnvironment#getArgument(String)} and converted to the
 * declared type of the method parameter.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ArgumentMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final ArgumentConverter argumentConverter;

	/**
	 * Constructor with an
	 * {@link org.springframework.http.converter.HttpMessageConverter} to convert
	 * Map-based input arguments to higher level Objects.
	 */
	public ArgumentMethodArgumentResolver(GenericHttpMessageConverter<Object> converter) {
		this.argumentConverter = new MessageConverterArgumentConverter(converter);
	}

	/**
	 * Variant of
	 * {@link #ArgumentMethodArgumentResolver(GenericHttpMessageConverter)}
	 * to use an {@link Encoder} and {@link Decoder} to convert input arguments.
	 */
	public ArgumentMethodArgumentResolver(Decoder<Object> decoder, Encoder<Object> encoder) {
		this.argumentConverter = new CodecArgumentConverter(decoder, encoder);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterAnnotation(Argument.class) != null;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		Argument annotation = parameter.getParameterAnnotation(Argument.class);
		Assert.notNull(annotation, "No @Argument annotation");
		String name = annotation.name();
		if (!StringUtils.hasText(name)) {
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument of type [" + parameter.getNestedParameterType().getName() +
								"] not specified, and parameter name information not found in class file either.");
			}
		}

		Object rawValue = (ValueConstants.DEFAULT_NONE.equals(annotation.defaultValue()) ?
				environment.getArgument(name) :
				environment.getArgumentOrDefault(name, annotation.defaultValue()));

		Class<?> parameterType = parameter.getParameterType();

		if (rawValue == null) {
			if (annotation.required()) {
				throw new MissingArgumentException(name, parameter);
			}
			if (parameterType.equals(Optional.class)) {
				return Optional.empty();
			}
			return null;
		}

		if (parameterType.isAssignableFrom(rawValue.getClass())) {
			return returnValue(rawValue, parameterType);
		}

		if (rawValue instanceof List) {
			Assert.isAssignable(List.class, parameterType,
					"Argument '" + name + "' is a List while the @Argument method parameter is " + parameterType);
			List<?> valueList = (List<?>) rawValue;
			Class<?> elementType = parameter.nestedIfOptional().getNestedParameterType();
			if (valueList.isEmpty() || elementType.isAssignableFrom(valueList.get(0).getClass())) {
				return returnValue(rawValue, parameterType);
			}
		}

		Object decodedValue = this.argumentConverter.convert(rawValue, parameter);
		Assert.notNull(decodedValue, "Argument '" + name + "' with raw value '" + rawValue +  "'was decoded to null");
		return returnValue(decodedValue, parameterType);
	}

	private Object returnValue(Object value, Class<?> parameterType) {
		return (parameterType.equals(Optional.class) ? Optional.of(value) : value);
	}


	/**
	 * Contract to abstract use of an HttpMessageConverter vs Encoder/Decoder.
	 */
	private interface ArgumentConverter {

		@Nullable
		Object convert(Object rawValue, MethodParameter targetParameter) throws Exception;

	}


	/**
	 * HttpMessageConverter based implementation of ArgumentConverter.
	 */
	private static class MessageConverterArgumentConverter implements ArgumentConverter {

		private final GenericHttpMessageConverter<Object> converter;

		public MessageConverterArgumentConverter(GenericHttpMessageConverter<Object> converter) {
			this.converter = converter;
		}

		@Override
		public Object convert(Object rawValue, MethodParameter targetParameter) throws IOException {
			HttpOutputMessageAdapter outMessage = new HttpOutputMessageAdapter();
			this.converter.write(rawValue, MediaType.APPLICATION_JSON, outMessage);

			HttpInputMessageAdapter inMessage = new HttpInputMessageAdapter(outMessage);
			return this.converter.read(targetParameter.getGenericParameterType(), rawValue.getClass(), inMessage);
		}
	}


	/**
	 * Encoder/Decoder based implementation of ArgumentConverter.
	 */
	private static class CodecArgumentConverter implements ArgumentConverter {

		private final Decoder<Object> decoder;

		private final Encoder<Object> encoder;

		public CodecArgumentConverter(Decoder<Object> decoder, Encoder<Object> encoder) {
			Assert.notNull(decoder, "Decoder is required");
			Assert.notNull(encoder, "Encoder is required");
			this.decoder = decoder;
			this.encoder = encoder;
		}

		@Override
		public Object convert(Object rawValue, MethodParameter targetParameter) {
			DataBuffer dataBuffer = this.encoder.encodeValue(
					rawValue, DefaultDataBufferFactory.sharedInstance, ResolvableType.forInstance(rawValue),
					MimeTypeUtils.APPLICATION_JSON, Collections.emptyMap());

			return this.decoder.decode(
					dataBuffer, ResolvableType.forMethodParameter(targetParameter.nestedIfOptional()),
					MimeTypeUtils.APPLICATION_JSON, Collections.emptyMap());
		}
	}


	private static class HttpInputMessageAdapter extends ByteArrayInputStream implements HttpInputMessage {

		HttpInputMessageAdapter(HttpOutputMessageAdapter messageAdapter) {
			super(messageAdapter.toByteArray());
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

	private static class HttpOutputMessageAdapter extends ByteArrayOutputStream implements HttpOutputMessage {

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

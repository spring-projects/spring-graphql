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

package org.springframework.graphql.server.webflux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;

/**
 * Helper class for encoding and decoding GraphQL messages in HTTP transport.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
final class HttpCodecDelegate {

	private static final ResolvableType REQUEST_TYPE = ResolvableType.forClass(SerializableGraphQlRequest.class);

	private static final ResolvableType RESPONSE_TYPE = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);

	private static final Function<DecodingException, ServerWebInputException> DECODING_MAPPER =
			(ex) -> new ServerWebInputException("Failed to read HTTP message", null, ex);

	private final AtomicReference<@Nullable Decoder<?>> decoder = new AtomicReference<>();

	private final AtomicReference<@Nullable EncoderHttpMessageWriter<?>> writer = new AtomicReference<>();

	/*
	 * Will use the custom codecs provided
	 */
	HttpCodecDelegate(CodecConfigurer codecConfigurer) {
		Assert.notNull(codecConfigurer, "CodecConfigurer is required");
		this.decoder.set(findJsonDecoder(codecConfigurer.getReaders()));
		this.writer.set(new EncoderHttpMessageWriter<>(findJsonEncoder(codecConfigurer.getWriters())));
	}

	/*
	 * Codecs will be resolved dynamically from the ones available
	 */
	HttpCodecDelegate() {
	}

	private Decoder<?> getDecoder(ServerRequest serverRequest) {
		Decoder<?> decoder = this.decoder.get();
		if (decoder != null) {
			return decoder;
		}
		Decoder<?> resolved = findJsonDecoder(serverRequest.messageReaders());
		return this.decoder.compareAndSet(null, resolved) ? resolved : Objects.requireNonNull(this.decoder.get());
	}

	private EncoderHttpMessageWriter<?> getWriter(BodyInserter.Context context) {
		EncoderHttpMessageWriter<?> writer = this.writer.get();
		if (writer != null) {
			return writer;
		}
		EncoderHttpMessageWriter<?> resolved = new EncoderHttpMessageWriter<>(findJsonEncoder(context.messageWriters()));
		return this.writer.compareAndSet(null, resolved) ? resolved : Objects.requireNonNull(this.writer.get());
	}

	private static Decoder<?> findJsonDecoder(List<HttpMessageReader<?>> readers) {
		return readers.stream()
				.filter((reader) -> reader.canRead(REQUEST_TYPE, MediaType.APPLICATION_JSON))
				.<Decoder<?>>map((reader) -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Decoder could be found"));
	}

	private static Encoder<?> findJsonEncoder(List<HttpMessageWriter<?>> writers) {
		return writers.stream()
				.filter((writer) -> writer.canWrite(RESPONSE_TYPE, MediaType.APPLICATION_JSON))
				.map((writer) -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No JSON Encoder could be found"));
	}

	@SuppressWarnings("unchecked")
	Mono<Void> write(Map<String, Object> resultMap, ReactiveHttpOutputMessage outputMessage, BodyInserter.Context context) {
		EncoderHttpMessageWriter<Map<String, Object>> writer = (EncoderHttpMessageWriter<Map<String, Object>>) getWriter(context);
		return writer.write(Mono.just(resultMap), RESPONSE_TYPE, MediaType.APPLICATION_JSON, outputMessage, context.hints());
	}

	/**
	 * Whether the resolved decoder can read the given content type.
	 * @since 2.1.0
	 */
	boolean canDecode(ServerRequest request, MimeType contentType) {
		return getDecoder(request).canDecode(REQUEST_TYPE, contentType);
	}

	/**
	 * Return the media types supported by the resolved decoder, e.g. for reporting
	 * in a {@code 415 Unsupported Media Type} response.
	 * @since 2.1.0
	 */
	List<MediaType> getSupportedMediaTypes(ServerRequest request) {
		return getDecoder(request).getDecodableMimeTypes().stream()
				.map((mimeType) -> (mimeType instanceof MediaType mediaType) ? mediaType : new MediaType(mimeType))
				.toList();
	}

	@SuppressWarnings("unchecked")
	Mono<SerializableGraphQlRequest> decode(ServerRequest request, Publisher<DataBuffer> inputStream, MediaType contentType) {
		Decoder<SerializableGraphQlRequest> decoder = (Decoder<SerializableGraphQlRequest>) getDecoder(request);
		return decoder.decodeToMono(inputStream, REQUEST_TYPE, contentType, null)
				.onErrorMap(DecodingException.class, DECODING_MAPPER);
	}

	@SuppressWarnings("unchecked")
	Mono<Map<String, @Nullable Object>> decodeQueryParam(ServerRequest request, String json) {
		DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(json.getBytes(StandardCharsets.UTF_8));
		Decoder<Map<String, @Nullable Object>> decoder = (Decoder<Map<String, @Nullable Object>>) getDecoder(request);
		return decoder.decodeToMono(Mono.just(buffer), RESPONSE_TYPE, MimeTypeUtils.APPLICATION_JSON, null)
				.onErrorMap(DecodingException.class, DECODING_MAPPER);
	}

}

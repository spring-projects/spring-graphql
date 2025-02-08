/*
 * Copyright 2020-2024 the original author or authors.
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


import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

/**
 * Abstract base class for GraphQL over HTTP handlers.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public abstract class AbstractGraphQlHttpHandler {

	protected final Log logger = LogFactory.getLog(getClass());

	private static final MediaType APPLICATION_GRAPHQL = MediaType.parseMediaType("application/graphql");

	private final WebGraphQlHandler graphQlHandler;

	@Nullable
	private final HttpCodecDelegate codecDelegate;


	protected AbstractGraphQlHttpHandler(
			WebGraphQlHandler graphQlHandler, @Nullable CodecConfigurer codecConfigurer) {

		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
		this.codecDelegate = (codecConfigurer != null) ? new HttpCodecDelegate(codecConfigurer) : null;
	}


	/**
	 * Handle GraphQL over HTTP request.
	 * @param request the current request
	 * @return the resulting response
	 */
	public Mono<ServerResponse> handleRequest(ServerRequest request) {
		return readRequest(request)
				.flatMap((body) -> {
					WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
							request.uri(), request.headers().asHttpHeaders(), request.cookies(),
							request.remoteAddress().orElse(null), request.attributes(), body,
							request.exchange().getRequest().getId(),
							request.exchange().getLocaleContext().getLocale());

					if (this.logger.isDebugEnabled()) {
						this.logger.debug("Executing: " + graphQlRequest);
					}

					return this.graphQlHandler.handleRequest(graphQlRequest);
				})
				.flatMap((response) -> {
					if (this.logger.isDebugEnabled()) {
						List<ResponseError> errors = response.getErrors();
						this.logger.debug("Execution result " +
								(!CollectionUtils.isEmpty(errors) ? "has errors: " + errors : "is ready") + ".");
					}

					return prepareResponse(request, response);
				});
	}

	private Mono<SerializableGraphQlRequest> readRequest(ServerRequest serverRequest) {
		if (this.codecDelegate != null) {
			MediaType contentType = serverRequest.headers().contentType().orElse(MediaType.APPLICATION_JSON);
			return this.codecDelegate.decode(serverRequest.bodyToFlux(DataBuffer.class), contentType);
		}
		else {
			return serverRequest.bodyToMono(SerializableGraphQlRequest.class)
					.onErrorResume(
							UnsupportedMediaTypeStatusException.class,
							(ex) -> applyApplicationGraphQlFallback(ex, serverRequest));
		}
	}

	private static Mono<SerializableGraphQlRequest> applyApplicationGraphQlFallback(
			UnsupportedMediaTypeStatusException ex, ServerRequest request) {

		String contentTypeHeader = request.headers().firstHeader(HttpHeaders.CONTENT_TYPE);
		if (StringUtils.hasText(contentTypeHeader)) {
			MediaType contentType = MediaType.parseMediaType(contentTypeHeader);

			// Spec requires application/json but some clients still use application/graphql
			return APPLICATION_GRAPHQL.includes(contentType) ? ServerRequest.from(request)
					.headers((headers) -> headers.setContentType(MediaType.APPLICATION_JSON))
					.body(request.bodyToFlux(DataBuffer.class))
					.build()
					.bodyToMono(SerializableGraphQlRequest.class)
					.log() : Mono.error(ex);
		}
		return Mono.error(ex);
	}

	/**
	 * Prepare the {@link ServerResponse} for the given GraphQL response.
	 * @param request the current request
	 * @param response the GraphQL response
	 * @return the server response
	 */
	protected abstract Mono<ServerResponse> prepareResponse(ServerRequest request, WebGraphQlResponse response);

	/**
	 * Encode the GraphQL response if custom codecs were provided, or return the result map.
	 * @param response the GraphQL response
	 * @return the encoded response or the result map
	 */
	protected Object encodeResponseIfNecessary(WebGraphQlResponse response) {
		Map<String, Object> resultMap = response.toMap();
		return (this.codecDelegate != null) ? encode(resultMap) : resultMap;
	}

	/**
	 * Encode the result map.
	 * <p>This method assumes that a {@link CodecConfigurer} has been provided.
	 * @param resultMap the result to encode
	 * @return the encoded result map
	 */
	protected DataBuffer encode(Map<String, Object> resultMap) {
		Assert.state(this.codecDelegate != null, "CodecConfigurer was not provided");
		return this.codecDelegate.encode(resultMap);
	}

}

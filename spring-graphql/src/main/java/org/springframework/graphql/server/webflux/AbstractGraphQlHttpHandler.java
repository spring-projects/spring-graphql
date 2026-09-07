/*
 * Copyright 2020-present the original author or authors.
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


import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import graphql.GraphQLError;
import graphql.language.OperationDefinition;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.OperationNotAllowedException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserter;
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

	private static final MediaType APPLICATION_GRAPHQL = MediaType.parseMediaType("application/graphql");

	private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.QUERY);

	protected final Log logger = LogFactory.getLog(getClass());

	private final WebGraphQlHandler graphQlHandler;

	private final HttpCodecDelegate codecDelegate;


	protected AbstractGraphQlHttpHandler(
			WebGraphQlHandler graphQlHandler, @Nullable CodecConfigurer codecConfigurer) {

		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
		this.codecDelegate = (codecConfigurer != null) ? new HttpCodecDelegate(codecConfigurer) : new HttpCodecDelegate();
	}

	/**
	 * Prepare the {@link ServerResponse} for the given GraphQL response.
	 * @param request the current request
	 * @param response the GraphQL response
	 * @return the server response
	 */
	protected abstract Mono<ServerResponse> prepareResponse(ServerRequest request, WebGraphQlResponse response);

	/**
	 * Return the GraphQL operation types that this handler supports, regardless
	 * of the semantics of any particular request.
	 * @since 2.1.0
	 */
	protected abstract Set<OperationDefinition.Operation> getSupportedOperations();

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

					graphQlRequest.allowedOperations(getAllowedOperations(request));

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

	/**
	 * Intersect {@link #getSupportedOperations() this handler's supported operations}
	 * with the semantics of the current HTTP method. Here, safe HTTP methods must not
	 * execute mutations.
	 */
	private Set<OperationDefinition.Operation> getAllowedOperations(ServerRequest request) {
		Set<OperationDefinition.Operation> supported = getSupportedOperations();
		if (!SAFE_METHODS.contains(request.method())) {
			return supported;
		}
		EnumSet<OperationDefinition.Operation> allowed = EnumSet.copyOf(supported);
		allowed.remove(OperationDefinition.Operation.MUTATION);
		return allowed;
	}

	private Mono<SerializableGraphQlRequest> readRequest(ServerRequest request) {
		return (request.method() == HttpMethod.GET) ? readRequestFromHttpQueryParams(request) : readRequestFromHttpBody(request);
	}

	private Mono<SerializableGraphQlRequest> readRequestFromHttpQueryParams(ServerRequest request) {
		SerializableGraphQlRequest graphQlRequest = new SerializableGraphQlRequest();
		request.queryParam("query").filter(StringUtils::hasText).ifPresent(graphQlRequest::setQuery);
		request.queryParam("operationName").filter(StringUtils::hasText).ifPresent(graphQlRequest::setOperationName);

		Mono<Void> variablesMono = request.queryParam("variables").filter(StringUtils::hasText)
				.map((json) -> this.codecDelegate
						.decodeQueryParam(request, json)
						.doOnNext(graphQlRequest::setVariables).then())
				.orElse(Mono.empty());

		Mono<Void> extensionsMono = request.queryParam("extensions").filter(StringUtils::hasText)
				.map((json) -> this.codecDelegate
						.decodeQueryParam(request, json)
						.doOnNext(graphQlRequest::setExtensions).then())
				.orElse(Mono.empty());

		return Mono.when(variablesMono, extensionsMono).thenReturn(graphQlRequest);
	}

	private Mono<SerializableGraphQlRequest> readRequestFromHttpBody(ServerRequest serverRequest) {
		ServerRequest.Headers headers = serverRequest.headers();
		MediaType contentType;
		try {
			contentType = headers.contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
		}
		catch (InvalidMediaTypeException ex) {
			throw new UnsupportedMediaTypeStatusException("Could not parse " +
					"Content-Type [" + headers.firstHeader(HttpHeaders.CONTENT_TYPE) + "]: " + ex.getMessage());
		}
		// Spec requires application/json but some clients still use application/graphql
		if (APPLICATION_GRAPHQL.includes(contentType)) {
			contentType = MediaType.APPLICATION_JSON;
		}
		if (this.codecDelegate.canDecode(serverRequest, contentType)) {
			return this.codecDelegate.decode(serverRequest, serverRequest.bodyToFlux(DataBuffer.class), contentType);
		}
		throw new UnsupportedMediaTypeStatusException(
				contentType, this.codecDelegate.getSupportedMediaTypes(serverRequest), serverRequest.method());
	}

	/**
	 * Find an {@link OperationNotAllowedException} rejection in the response errors, if any.
	 * <p>Must be called on the {@link WebGraphQlResponse} before it is turned into
	 * a {@code Map}, since the exception type is not preserved past that point.
	 * @param response the GraphQL response before serialization
	 * @since 2.1.0
	 */
	protected static @Nullable OperationNotAllowedException findRejection(WebGraphQlResponse response) {
		for (GraphQLError error : response.getExecutionResult().getErrors()) {
			if (error instanceof OperationNotAllowedException ex) {
				return ex;
			}
		}
		return null;
	}

	/**
	 * Create a body inserter function that knows how to write the GraphQL response as a JSON HTTP response body.
	 * @param response the response to write out
	 * @return the body inserter to use for writing the GraphQL response to the HTTP message
	 * @since 2.1.0
	 */
	protected BodyInserter<WebGraphQlResponse, ServerHttpResponse> bodyInserter(WebGraphQlResponse response) {
		return (outputMessage, context) -> {
			DataBuffer buffer = AbstractGraphQlHttpHandler.this.codecDelegate.encode(response.toMap(), context);
			return outputMessage.writeWith(Flux.just(buffer));
		};
	}

}

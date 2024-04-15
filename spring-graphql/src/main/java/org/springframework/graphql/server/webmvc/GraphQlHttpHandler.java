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

package org.springframework.graphql.server.webmvc;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jakarta.servlet.ServletException;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler to expose as a WebMvc functional endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class GraphQlHttpHandler extends AbstractGraphQlHttpHandler {

	@SuppressWarnings("removal")
	private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
			Arrays.asList(MediaType.APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL);


	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		super(graphQlHandler, null);
	}

	/**
	 * Create a new instance with a custom message converter.
	 * <p>If no converter is provided, this will use
	 * {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer#configureMessageConverters(List) the one configured in the web framework}.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @param messageConverter custom {@link HttpMessageConverter} to be used for encoding and decoding GraphQL payloads
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> messageConverter) {
		super(graphQlHandler, messageConverter);
	}

	/**
	 * Handle GraphQL requests over HTTP.
	 * @param serverRequest the incoming HTTP request
	 * @return the HTTP response
	 * @throws ServletException may be raised when reading the request body, e.g.
	 * {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handleRequest(ServerRequest serverRequest) throws ServletException {

		WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
				serverRequest.uri(), serverRequest.headers().asHttpHeaders(), initCookies(serverRequest),
				serverRequest.remoteAddress().orElse(null),
				serverRequest.attributes(), readBody(serverRequest), this.idGenerator.generateId().toString(),
				LocaleContextHolder.getLocale());

		if (logger.isDebugEnabled()) {
			logger.debug("Executing: " + graphQlRequest);
		}

		CompletableFuture<ServerResponse> future = this.graphQlHandler.handleRequest(graphQlRequest)
				.map((response) -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Execution complete");
					}
					MediaType contentType = selectResponseMediaType(serverRequest);
					ServerResponse.BodyBuilder builder = ServerResponse.ok();
					builder.headers((headers) -> headers.putAll(response.getResponseHeaders()));
					builder.contentType(contentType);

					if (this.messageConverter != null) {
						return builder.build(writeFunction(contentType, response));
					}
					else {
						return builder.body(response.toMap());
					}
				})
				.toFuture();

		if (future.isDone()) {
			try {
				return future.get();
			}
			catch (ExecutionException ex) {
				throw new ServletException(ex.getCause());
			}
			catch (InterruptedException ex) {
				throw new ServletException(ex);
			}
		}

		return ServerResponse.async(future);
	}

	private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
		for (MediaType accepted : serverRequest.headers().accept()) {
			if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
				return accepted;
			}
		}
		return MediaType.APPLICATION_JSON;
	}

	private ServerResponse.HeadersBuilder.WriteFunction writeFunction(MediaType contentType, GraphQlResponse response) {
		return (servletRequest, servletResponse) -> {
			if (messageConverter != null) {
				ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(servletResponse);
				messageConverter.write(response.toMap(), contentType, httpResponse);
				return null;
			}
			else {
				throw new HttpMediaTypeNotSupportedException(contentType, SUPPORTED_MEDIA_TYPES, HttpMethod.POST);
			}
		};
	}

}

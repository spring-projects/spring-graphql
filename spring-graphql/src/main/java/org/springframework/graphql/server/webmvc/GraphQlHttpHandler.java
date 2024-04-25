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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jakarta.servlet.ServletException;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
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
	private static final List<MediaType> SUPPORTED_MEDIA_TYPES = List.of(
			MediaType.APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL);


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
	 * {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer#configureMessageConverters(List)
	 * the one configured in the web framework}.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @param converter custom {@link HttpMessageConverter} to read and write GraphQL payloads
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> converter) {
		super(graphQlHandler, converter);
	}


	@Override
	protected ServerResponse prepareResponse(ServerRequest request, Mono<WebGraphQlResponse> responseMono)
			throws ServletException {

		CompletableFuture<ServerResponse> future = responseMono.map((response) -> {
			MediaType contentType = selectResponseMediaType(request);
			ServerResponse.BodyBuilder builder = ServerResponse.ok();
			builder.headers((headers) -> headers.putAll(response.getResponseHeaders()));
			builder.contentType(contentType);

			if (getMessageConverter() != null) {
				return builder.build(writeFunction(getMessageConverter(), contentType, response.toMap()));
			}
			else {
				return builder.body(response.toMap());
			}
		}).toFuture();

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

	private static ServerResponse.HeadersBuilder.WriteFunction writeFunction(
			HttpMessageConverter<Object> converter, MediaType contentType, Map<String, Object> resultMap) {

		return (servletRequest, servletResponse) -> {
			ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(servletResponse);
			converter.write(resultMap, contentType, httpResponse);
			return null;
		};
	}

}

/*
 * Copyright 2020-2025 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.web.server.NotAcceptableStatusException;
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

	private static final MediaType APPLICATION_GRAPHQL =
			new MediaType("application", "graphql+json");

	private static final List<MediaType> SUPPORTED_MEDIA_TYPES = List.of(
			MediaTypes.APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, APPLICATION_GRAPHQL);

	private boolean httpOkOnValidationErrors = false;

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		super(graphQlHandler, null);
	}

	/**
	 * Create a new instance with a custom message converter for GraphQL payloads.
	 * <p>If no converter is provided, the handler will use
	 * {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer#configureMessageConverters(List)
	 * the one configured for web use}.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @param converter the converter to use to read and write GraphQL payloads
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> converter) {
		super(graphQlHandler, converter);
	}

	/**
	 * Return whether this HTTP handler should use HTTP 200 OK responses if an error occurs before
	 * the GraphQL request execution phase starts; for example, if JSON parsing, GraphQL document parsing,
	 * or GraphQL document validation fail.
	 * <p>This option only applies to {@link MediaTypes#APPLICATION_GRAPHQL_RESPONSE} responses,
	 * as legacy {@link MediaType#APPLICATION_JSON} responses always use HTTP 200 OK in such cases.
	 * Enabling this option means the server will not conform to the "GraphQL over HTTP specification".
	 * <p>By default, this is set to {@code false}.
	 * @since 1.4.0
	 * @see <a href="https://graphql.github.io/graphql-over-http/draft/#sec-application-graphql-response-json">GraphQL over HTTP specification</a>
	 */
	public boolean isHttpOkOnValidationErrors() {
		return this.httpOkOnValidationErrors;
	}

	/**
	 * Set whether this HTTP handler should use HTTP 200 OK responses if an error occurs before
	 * the GraphQL request execution phase starts.
	 * @param httpOkOnValidationErrors whether "HTTP 200 OK" responses should always be used
	 * @since 1.4.0
	 * @deprecated since 1.4, will be made {@code false} permanently in a future release
	 * @see #isHttpOkOnValidationErrors
	 */
	@Deprecated(since = "1.4.0", forRemoval = true)
	public void setHttpOkOnValidationErrors(boolean httpOkOnValidationErrors) {
		this.httpOkOnValidationErrors = httpOkOnValidationErrors;
	}


	@Override
	protected ServerResponse prepareResponse(ServerRequest request, Mono<WebGraphQlResponse> responseMono) {

		Mono<ServerResponse> mono = responseMono.map((response) -> {
			MediaType contentType = selectResponseMediaType(request);
			HttpStatus responseStatus = selectResponseStatus(response, contentType);
			ServerResponse.BodyBuilder builder = ServerResponse.status(responseStatus);
			builder.headers((headers) -> headers.putAll(response.getResponseHeaders()));
			builder.contentType(contentType);

			Map<String, Object> resultMap = response.toMap();
			ServerResponse.HeadersBuilder.WriteFunction writer = getWriteFunction(resultMap, contentType);
			return (writer != null) ? builder.build(writer) : builder.body(resultMap);
		});

		return ServerResponse.async(mono.toFuture());
	}

	protected HttpStatus selectResponseStatus(WebGraphQlResponse response, MediaType responseMediaType) {
		if (!isHttpOkOnValidationErrors()
				&& !response.getExecutionResult().isDataPresent()
				&& MediaTypes.APPLICATION_GRAPHQL_RESPONSE.equals(responseMediaType)) {
			return HttpStatus.BAD_REQUEST;
		}
		return HttpStatus.OK;
	}

	private static MediaType selectResponseMediaType(ServerRequest request) {
		ServerRequest.Headers headers = request.headers();
		List<MediaType> acceptedMediaTypes;
		try {
			acceptedMediaTypes = headers.accept();
		}
		catch (InvalidMediaTypeException ex) {
			throw new NotAcceptableStatusException("Could not parse " +
					"Accept header [" + headers.firstHeader(HttpHeaders.ACCEPT) + "]: " + ex.getMessage());
		}
		for (MediaType mediaType : acceptedMediaTypes) {
			if (SUPPORTED_MEDIA_TYPES.contains(mediaType)) {
				return mediaType;
			}
		}
		return MediaType.APPLICATION_JSON;
	}

}

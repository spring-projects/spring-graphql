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

package org.springframework.graphql.server.webflux;

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * WebFlux.fn Handler for GraphQL over HTTP requests.
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
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @param codecConfigurer codec configurer for JSON encoding and decoding
	 */
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, CodecConfigurer codecConfigurer) {
		super(graphQlHandler, codecConfigurer);
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

	protected Mono<ServerResponse> prepareResponse(ServerRequest request, WebGraphQlResponse response) {
		MediaType responseMediaType = selectResponseMediaType(request);
		HttpStatus responseStatus = selectResponseStatus(response, responseMediaType);
		ServerResponse.BodyBuilder builder = ServerResponse.status(responseStatus);
		builder.headers((headers) -> headers.putAll(response.getResponseHeaders()));
		builder.contentType(responseMediaType);
		return builder.bodyValue(encodeResponseIfNecessary(response));
	}

	protected HttpStatus selectResponseStatus(WebGraphQlResponse response, MediaType responseMediaType) {
		if (!isHttpOkOnValidationErrors()
				&& !response.getExecutionResult().isDataPresent()
				&& MediaTypes.APPLICATION_GRAPHQL_RESPONSE.equals(responseMediaType)) {
			return HttpStatus.BAD_REQUEST;
		}
		return HttpStatus.OK;
	}

	private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
		for (MediaType accepted : serverRequest.headers().accept()) {
			if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
				return accepted;
			}
		}
		return MediaType.APPLICATION_JSON;
	}

}

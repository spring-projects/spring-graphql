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

package org.springframework.graphql.server.webmvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import graphql.language.OperationDefinition;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.execution.OperationNotAllowedException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.util.Assert;
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

	private static final Set<OperationDefinition.Operation> SUPPORTED_OPERATIONS =
			Set.of(OperationDefinition.Operation.QUERY, OperationDefinition.Operation.MUTATION);

	private boolean httpOkOnValidationErrors = false;

	private final Set<HttpMethod> httpMethods;

	/**
	 * Create a new instance.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @deprecated since 2.1.0 in favor of {@link #builder(WebGraphQlHandler)}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
		this(graphQlHandler, null, Set.of(HttpMethod.POST));
	}

	/**
	 * Create a new instance with a custom message converter for GraphQL payloads.
	 * <p>If no converter is provided, the handler will use
	 * {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer#configureMessageConverters(HttpMessageConverters.ServerBuilder)}
	 * the one configured for web use}.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @param converter the converter to use to read and write GraphQL payloads
	 * @deprecated since 2.1.0 in favor of {@link #builder(WebGraphQlHandler)}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public GraphQlHttpHandler(WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> converter) {
		this(graphQlHandler, converter, Set.of(HttpMethod.POST));
	}

	private GraphQlHttpHandler(
			WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> converter, Set<HttpMethod> httpMethods) {

		super(graphQlHandler, converter);
		Assert.notEmpty(httpMethods, "'httpMethods' must not be empty");
		this.httpMethods = httpMethods;
	}

	/**
	 * Return a builder to create a {@link GraphQlHttpHandler}, e.g. to configure
	 * which HTTP methods it should support.
	 * @param graphQlHandler common handler for GraphQL over HTTP requests
	 * @since 2.1.0
	 */
	public static Builder builder(WebGraphQlHandler graphQlHandler) {
		return new Builder(graphQlHandler);
	}

	/**
	 * Return the HTTP methods this handler is configured to support.
	 * <p>Applications should use this value when configuring the matching
	 * {@link GraphQlRequestPredicates#graphQlHttp(String, Set) RequestPredicate},
	 * so that both stay in sync.
	 * @since 2.1.0
	 */
	public Set<HttpMethod> getHttpMethods() {
		return this.httpMethods;
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


	@Override
	protected ServerResponse prepareResponse(ServerRequest request, Mono<WebGraphQlResponse> responseMono) {

		Mono<ServerResponse> mono = responseMono.map((response) -> {
			MediaType contentType = selectResponseMediaType(request);
			HttpStatus responseStatus = selectResponseStatus(request, response, contentType);
			ServerResponse.BodyBuilder builder = ServerResponse.status(responseStatus);
			if (responseStatus == HttpStatus.METHOD_NOT_ALLOWED) {
				builder.allow(this.httpMethods);
			}
			builder.headers((headers) -> headers.putAll(response.getResponseHeaders()));
			builder.contentType(contentType);

			Map<String, Object> resultMap = response.toMap();
			return builder.build(getWriteFunction(request, resultMap, contentType));
		});

		return ServerResponse.async(mono.toFuture());
	}

	/**
	 * Select the HTTP response status, taking into account any rejection of the
	 * request's operation type based on the semantics of the current HTTP method.
	 * @param request the HTTP request
	 * @param response the GraphQL response
	 * @param responseMediaType the HTTP response media type
	 */
	protected HttpStatus selectResponseStatus(ServerRequest request, WebGraphQlResponse response, MediaType responseMediaType) {
		OperationNotAllowedException rejection = findRejection(response);
		if (rejection != null && rejection.getOperation() == OperationDefinition.Operation.MUTATION) {
			if (request.method() == HttpMethod.GET) {
				return HttpStatus.METHOD_NOT_ALLOWED;
			}
			if (request.method() == HttpMethod.QUERY) {
				return HttpStatus.UNPROCESSABLE_CONTENT;
			}
		}
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

	@Override
	protected Set<OperationDefinition.Operation> getSupportedOperations() {
		return SUPPORTED_OPERATIONS;
	}


	/**
	 * Builder for {@link GraphQlHttpHandler}.
	 * @since 2.1.0
	 */
	public static final class Builder {

		private final WebGraphQlHandler graphQlHandler;

		private @Nullable HttpMessageConverter<?> converter;

		private Set<HttpMethod> httpMethods = Set.of(HttpMethod.POST);

		private Builder(WebGraphQlHandler graphQlHandler) {
			this.graphQlHandler = graphQlHandler;
		}

		/**
		 * Set the converter to use to read and write GraphQL payloads.
		 * <p>If not set, the handler will use the one configured for web use.
		 * @param converter the converter to use
		 */
		public Builder messageConverter(HttpMessageConverter<?> converter) {
			this.converter = converter;
			return this;
		}

		/**
		 * Set the HTTP methods that the handler should support.
		 * <p>By default, only {@link HttpMethod#POST} is supported. Enabling
		 * {@link HttpMethod#GET} means that queries and their parameters are
		 * exposed through the request URL; consider the security implications,
		 * for example URLs being logged or cached, before enabling it.
		 * {@link HttpMethod#QUERY} carries the same JSON body as {@code POST}
		 * but, like {@code GET}, must not execute mutations; a mutation sent
		 * over {@code QUERY} results in a {@code 422 Unprocessable Entity} response.
		 * @param httpMethods the HTTP methods to support
		 */
		public Builder httpMethods(HttpMethod... httpMethods) {
			this.httpMethods = Set.of(httpMethods);
			return this;
		}

		/**
		 * Build the {@link GraphQlHttpHandler}.
		 */
		public GraphQlHttpHandler build() {
			return new GraphQlHttpHandler(this.graphQlHandler, this.converter, this.httpMethods);
		}

	}

}

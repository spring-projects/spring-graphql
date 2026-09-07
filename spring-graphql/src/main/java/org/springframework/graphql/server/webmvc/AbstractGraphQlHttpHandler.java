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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import graphql.GraphQLError;
import graphql.language.OperationDefinition;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.OperationNotAllowedException;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.IdGenerator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

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

	private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.QUERY);

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

	private final WebGraphQlHandler graphQlHandler;

	private final AtomicReference<@Nullable HttpMessageConverter<Object>> jsonMessageConverter = new AtomicReference<>();


	@SuppressWarnings("unchecked")
	protected AbstractGraphQlHttpHandler(
			WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> messageConverter) {

		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
		if (messageConverter != null) {
			this.jsonMessageConverter.set((HttpMessageConverter<Object>) messageConverter);
		}
	}

	/**
	 * Prepare the {@link ServerResponse} for the given GraphQL response.
	 * @param request the current request
	 * @param responseMono the GraphQL response
	 * @return the server response
	 */
	protected abstract ServerResponse prepareResponse(
			ServerRequest request, Mono<WebGraphQlResponse> responseMono) throws ServletException;

	/**
	 * Return the GraphQL operation types that this handler supports, regardless
	 * of the semantics of any particular request.
	 * @since 2.1.0
	 */
	protected abstract Set<OperationDefinition.Operation> getSupportedOperations();

	/**
	 * Handle GraphQL over HTTP requests.
	 * @param request the current request
	 * @return the resulting response
	 * @throws ServletException may be raised when reading the request body, e.g.
	 * {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handleRequest(ServerRequest request) throws ServletException {

		WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
				request.uri(), request.headers().asHttpHeaders(), initCookies(request),
				request.remoteAddress().orElse(null),
				request.attributes(), readRequest(request), this.idGenerator.generateId().toString(),
				LocaleContextHolder.getLocale());

		graphQlRequest.allowedOperations(getAllowedOperations(request));

		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Executing: " + graphQlRequest);
		}

		Mono<WebGraphQlResponse> responseMono = this.graphQlHandler.handleRequest(graphQlRequest)
				.doOnNext((response) -> {
					if (this.logger.isDebugEnabled()) {
						List<ResponseError> errors = response.getErrors();
						this.logger.debug("Execution result " +
								(!CollectionUtils.isEmpty(errors) ? "has errors: " + errors : "is ready") + ".");
					}
				});

		return prepareResponse(request, responseMono);
	}

	private static MultiValueMap<String, HttpCookie> initCookies(ServerRequest serverRequest) {
		MultiValueMap<String, Cookie> source = serverRequest.cookies();
		MultiValueMap<String, HttpCookie> target = new LinkedMultiValueMap<>(source.size());
		source.values().forEach((cookieList) -> cookieList.forEach((cookie) -> {
			HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
			target.add(cookie.getName(), httpCookie);
		}));
		return target;
	}

	private GraphQlRequest readRequest(ServerRequest request) throws ServletException {
		return (request.method() == HttpMethod.GET) ? readRequestFromHttpQueryParams(request) : readRequestFromHttpBody(request);
	}

	@SuppressWarnings("NullAway")
	private SerializableGraphQlRequest readRequestFromHttpQueryParams(ServerRequest request) {
		SerializableGraphQlRequest graphQlRequest = new SerializableGraphQlRequest();
		request.param("query").filter(StringUtils::hasText).ifPresent(graphQlRequest::setQuery);
		request.param("operationName").filter(StringUtils::hasText).ifPresent(graphQlRequest::setOperationName);
		request.param("variables").filter(StringUtils::hasText)
				.ifPresent((json) -> graphQlRequest.setVariables(decodeQueryParameter(request, json)));
		request.param("extensions").filter(StringUtils::hasText)
				.ifPresent((json) -> graphQlRequest.setExtensions(decodeQueryParameter(request, json)));
		return graphQlRequest;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> decodeQueryParameter(ServerRequest request, String json) {
		HttpMessageConverter<Object> converter = resolveJsonConverter(request);
		try {
			HttpInputMessage message = new ByteArrayHttpInputMessage(json, MediaType.APPLICATION_JSON);
			return (Map<String, Object>) converter.read(Map.class, message);
		}
		catch (IOException | HttpMessageNotReadableException ex) {
			throw new ServerWebInputException("Failed to parse JSON in GraphQL query parameter", null, ex);
		}
	}

	private GraphQlRequest readRequestFromHttpBody(ServerRequest request) throws ServletException {
		try {
			HttpMessageConverter<Object> jsonConverter = resolveJsonConverter(request);
			ServerRequest.Headers headers = request.headers();
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
			if (jsonConverter.canRead(SerializableGraphQlRequest.class, contentType)) {
				ServerHttpRequest httpRequest = new ServletServerHttpRequest(request.servletRequest());
				return (GraphQlRequest) jsonConverter.read(SerializableGraphQlRequest.class, httpRequest);
			}
			throw new HttpMediaTypeNotSupportedException(
					contentType, jsonConverter.getSupportedMediaTypes(), request.method());
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}
	}

	/**
	 * Intersect {@link #getSupportedOperations() this handler's supported operations}
	 * with the semantics of the current HTTP method, e.g. safe methods must not
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


	/**
	 * Exposes a {@link ServerResponse.HeadersBuilder.WriteFunction} that writes
	 * the GraphQL response as JSON using a custom message converter,
	 * or one that is available in the configured message converters.
	 * @param request the server request
	 * @param resultMap the result map to write
	 * @param contentType to set the response content type to
	 * @return the write function
	 */
	protected ServerResponse.HeadersBuilder.WriteFunction getWriteFunction(
			ServerRequest request, Map<String, Object> resultMap, MediaType contentType) {

		return (req, res) -> {
			ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(res);
			resolveJsonConverter(request).write(resultMap, contentType, httpResponse);
			return null;
		};
	}

	@SuppressWarnings("unchecked")
	private HttpMessageConverter<Object> resolveJsonConverter(ServerRequest request) {
		HttpMessageConverter<Object> converter = this.jsonMessageConverter.get();
		if (converter != null) {
			return converter;
		}
		HttpMessageConverter<Object> resolved = (HttpMessageConverter<Object>) request.messageConverters()
				.stream()
				.filter((candidate) -> candidate.canRead(Map.class, MediaType.APPLICATION_JSON))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"No JSON HttpMessageConverter available to decode 'variables'/'extensions' " +
								"for GraphQL HTTP GET requests"));
		return this.jsonMessageConverter.compareAndSet(null, resolved) ?
				resolved : Objects.requireNonNull(this.jsonMessageConverter.get());
	}

	/**
	 * Find an {@link OperationNotAllowedException} rejection in the response errors, if any.
	 * <p>Must be called on the {@link WebGraphQlResponse} before it is serialized.
	 * @param response the GraphQL response
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
	 * {@link HttpInputMessage} adapting a JSON string extracted from a query parameter,
	 * for use with an {@link HttpMessageConverter}.
	 */
	private static class ByteArrayHttpInputMessage implements HttpInputMessage {

		private final byte[] body;

		private final HttpHeaders headers;

		ByteArrayHttpInputMessage(String json, MediaType contentType) {
			this.body = json.getBytes(StandardCharsets.UTF_8);
			this.headers = headersFor(contentType);
		}

		private static HttpHeaders headersFor(MediaType contentType) {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(contentType);
			return headers;
		}

		@Override
		public InputStream getBody() {
			return new ByteArrayInputStream(this.body);
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}
	}

}

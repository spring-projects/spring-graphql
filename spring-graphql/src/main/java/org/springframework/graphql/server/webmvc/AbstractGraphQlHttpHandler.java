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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
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
import org.springframework.web.servlet.ModelAndView;
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

	private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

	private final WebGraphQlHandler graphQlHandler;

	private final @Nullable HttpMessageConverter<Object> messageConverter;


	@SuppressWarnings("unchecked")
	protected AbstractGraphQlHttpHandler(
			WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> messageConverter) {

		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
		this.messageConverter = (HttpMessageConverter<Object>) messageConverter;
	}


	/**
	 * Exposes a {@link ServerResponse.HeadersBuilder.WriteFunction} that writes
	 * with the {@code HttpMessageConverter} provided to the constructor.
	 * @param resultMap the result map to write
	 * @param contentType to set the response content type to
	 * @return the write function, or {@code null} if a
	 * {@code HttpMessageConverter} was not provided to the constructor
	 */
	protected ServerResponse.HeadersBuilder.@Nullable WriteFunction getWriteFunction(
			Map<String, Object> resultMap, MediaType contentType) {

		return (this.messageConverter != null) ?
				new MessageConverterWriteFunction(resultMap, contentType, this.messageConverter) : null;
	}


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
				request.attributes(), readBody(request), this.idGenerator.generateId().toString(),
				LocaleContextHolder.getLocale());

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

	private GraphQlRequest readBody(ServerRequest request) throws ServletException {
		try {
			if (this.messageConverter != null) {
				ServerRequest.Headers headers = request.headers();
				MediaType contentType;
				try {
					contentType = headers.contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
				}
				catch (InvalidMediaTypeException ex) {
					throw new UnsupportedMediaTypeStatusException("Could not parse " +
							"Content-Type [" + headers.firstHeader(HttpHeaders.CONTENT_TYPE) + "]: " + ex.getMessage());
				}
				if (this.messageConverter.canRead(SerializableGraphQlRequest.class, contentType)) {
					ServerHttpRequest httpRequest = new ServletServerHttpRequest(request.servletRequest());
					return (GraphQlRequest) this.messageConverter.read(SerializableGraphQlRequest.class, httpRequest);
				}
				throw new HttpMediaTypeNotSupportedException(
						contentType, this.messageConverter.getSupportedMediaTypes(), request.method());
			}
			else {
				try {
					return request.body(SerializableGraphQlRequest.class);
				}
				catch (HttpMediaTypeNotSupportedException ex) {
					return applyApplicationGraphQlFallback(request, ex);
				}
			}
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}
	}

	private static SerializableGraphQlRequest applyApplicationGraphQlFallback(
			ServerRequest request, HttpMediaTypeNotSupportedException ex) throws HttpMediaTypeNotSupportedException {

		String contentTypeHeader = request.headers().firstHeader(HttpHeaders.CONTENT_TYPE);
		if (StringUtils.hasText(contentTypeHeader)) {
			MediaType contentType = MediaType.parseMediaType(contentTypeHeader);
			// Spec requires application/json but some clients still use application/graphql
			if (APPLICATION_GRAPHQL.includes(contentType)) {
				try {
					request = ServerRequest.from(request)
							.headers((headers) -> headers.setContentType(MediaType.APPLICATION_JSON))
							.body(request.body(byte[].class))
							.build();
					return request.body(SerializableGraphQlRequest.class);
				}
				catch (Throwable ex2) {
					// ignore
				}
			}
		}
		throw ex;
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
	 * WriteFunction that writes with a given, fixed {@link HttpMessageConverter}.
	 */
	private record MessageConverterWriteFunction(
			Map<String, Object> resultMap, MediaType contentType, HttpMessageConverter<Object> converter)
			implements ServerResponse.HeadersBuilder.WriteFunction {

		@Override
		public @Nullable ModelAndView write(HttpServletRequest request, HttpServletResponse response) throws Exception {
			ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
			this.converter.write(this.resultMap, this.contentType, httpResponse);
			return null;
		}
	}

}

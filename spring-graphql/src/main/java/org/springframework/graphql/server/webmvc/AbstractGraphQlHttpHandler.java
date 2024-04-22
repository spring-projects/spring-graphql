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

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.Assert;
import org.springframework.util.IdGenerator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Abstract class for GraphQL Handler implementations using the HTTP transport.
 *
 * @author Brian Clozel
 */
abstract class AbstractGraphQlHttpHandler {

	protected final Log logger = LogFactory.getLog(getClass());

	protected final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

	protected final WebGraphQlHandler graphQlHandler;

	@Nullable
	protected final HttpMessageConverter<Object> messageConverter;

	@SuppressWarnings("unchecked")
	AbstractGraphQlHttpHandler(WebGraphQlHandler graphQlHandler, @Nullable HttpMessageConverter<?> messageConverter) {
		Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
		this.graphQlHandler = graphQlHandler;
		this.messageConverter = (HttpMessageConverter<Object>) messageConverter;
	}

	protected static MultiValueMap<String, HttpCookie> initCookies(ServerRequest serverRequest) {
		MultiValueMap<String, Cookie> source = serverRequest.cookies();
		MultiValueMap<String, HttpCookie> target = new LinkedMultiValueMap<>(source.size());
		source.values().forEach((cookieList) -> cookieList.forEach((cookie) -> {
			HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
			target.add(cookie.getName(), httpCookie);
		}));
		return target;
	}

	protected GraphQlRequest readBody(ServerRequest request) throws ServletException {
		try {
			if (this.messageConverter != null) {
				MediaType contentType = request.headers().contentType().orElse(MediaType.APPLICATION_JSON);
				if (this.messageConverter.canRead(SerializableGraphQlRequest.class, contentType)) {
					return (GraphQlRequest) this.messageConverter.read(SerializableGraphQlRequest.class,
							new ServletServerHttpRequest(request.servletRequest()));
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

		// Spec requires application/json but some clients still use application/graphql
		if ("application/graphql".equals(request.headers().firstHeader(HttpHeaders.CONTENT_TYPE))) {
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
		throw ex;
	}

}

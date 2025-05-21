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

import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.graphql.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * {@link RequestPredicate} implementations tailored for GraphQL endpoints.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public final class GraphQlRequestPredicates {

	private static final Log logger = LogFactory.getLog(GraphQlRequestPredicates.class);

	private GraphQlRequestPredicates() {

	}

	/**
	 * Create a {@link RequestPredicate predicate} that matches GraphQL HTTP requests for the configured path.
	 * @param path the path on which the GraphQL HTTP endpoint is mapped
	 * @see GraphQlHttpHandler
	 */
	public static RequestPredicate graphQlHttp(String path) {
		return new GraphQlHttpRequestPredicate(
				path, List.of(MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE));
	}

	/**
	 * Create a {@link RequestPredicate predicate} that matches GraphQL SSE over HTTP requests for the configured path.
	 * @param path the path on which the GraphQL SSE endpoint is mapped
	 * @see GraphQlSseHandler
	 */
	public static RequestPredicate graphQlSse(String path) {
		return new GraphQlHttpRequestPredicate(path, List.of(MediaType.TEXT_EVENT_STREAM));
	}

	private static class GraphQlHttpRequestPredicate implements RequestPredicate {

		private static final MediaType APPLICATION_GRAPHQL = MediaType.parseMediaType("application/graphql");

		private final PathPattern pattern;

		private final List<MediaType> contentTypes;

		private final List<MediaType> acceptedMediaTypes;


		GraphQlHttpRequestPredicate(String path, List<MediaType> accepted) {
			Assert.notNull(path, "'path' must not be null");
			Assert.notEmpty(accepted, "'accepted' must not be empty");
			PathPatternParser parser = PathPatternParser.defaultInstance;
			path = parser.initFullPathPattern(path);
			this.pattern = parser.parse(path);
			this.contentTypes = List.of(MediaType.APPLICATION_JSON, APPLICATION_GRAPHQL);
			this.acceptedMediaTypes = accepted;
		}

		@Override
		public boolean test(ServerRequest request) {
			return httpMethodMatch(request, HttpMethod.POST)
					&& contentTypeMatch(request, this.contentTypes)
					&& acceptMatch(request, this.acceptedMediaTypes)
					&& pathMatch(request, this.pattern);
		}

		private static boolean httpMethodMatch(ServerRequest request, HttpMethod expected) {
			HttpMethod actual = resolveHttpMethod(request);
			boolean methodMatch = expected.equals(actual);
			traceMatch("Method", expected, actual, methodMatch);
			return methodMatch;
		}

		private static HttpMethod resolveHttpMethod(ServerRequest request) {
			if (CorsUtils.isPreFlightRequest(request.servletRequest())) {
				String httpMethod = request.headers().firstHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
				if (httpMethod != null) {
					return HttpMethod.valueOf(httpMethod);
				}
			}
			return request.method();
		}

		private static boolean contentTypeMatch(ServerRequest request, List<MediaType> contentTypes) {
			if (CorsUtils.isPreFlightRequest(request.servletRequest())) {
				return true;
			}
			ServerRequest.Headers headers = request.headers();
			MediaType actual;
			try {
				actual = headers.contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
			}
			catch (InvalidMediaTypeException ex) {
				throw new UnsupportedMediaTypeStatusException("Could not parse " +
						"Content-Type [" + headers.firstHeader(HttpHeaders.CONTENT_TYPE) + "]: " + ex.getMessage());
			}
			boolean contentTypeMatch = false;
			for (MediaType contentType : contentTypes) {
				contentTypeMatch = contentType.includes(actual);
				traceMatch("Content-Type", contentTypes, actual, contentTypeMatch);
				if (contentTypeMatch) {
					break;
				}
			}
			return contentTypeMatch;
		}

		private static boolean acceptMatch(ServerRequest request, List<MediaType> expected) {
			if (CorsUtils.isPreFlightRequest(request.servletRequest())) {
				return true;
			}
			ServerRequest.Headers headers = request.headers();
			List<MediaType> acceptedMediaTypes;
			try {
				acceptedMediaTypes = acceptedMediaTypes(headers);
			}
			catch (InvalidMediaTypeException ex) {
				throw new NotAcceptableStatusException("Could not parse " +
						"Accept header [" + headers.firstHeader(HttpHeaders.ACCEPT) + "]: " + ex.getMessage());
			}
			boolean match = false;
			outer:
			for (MediaType acceptedMediaType : acceptedMediaTypes) {
				for (MediaType mediaType : expected) {
					if (acceptedMediaType.isCompatibleWith(mediaType)) {
						match = true;
						break outer;
					}
				}
			}
			traceMatch("Accept", expected, acceptedMediaTypes, match);
			return match;
		}

		private static List<MediaType> acceptedMediaTypes(ServerRequest.Headers headers) {
			List<MediaType> acceptedMediaTypes = headers.accept();
			if (acceptedMediaTypes.isEmpty()) {
				acceptedMediaTypes = Collections.singletonList(MediaType.ALL);
			}
			else {
				MimeTypeUtils.sortBySpecificity(acceptedMediaTypes);
			}
			return acceptedMediaTypes;
		}

		private static boolean pathMatch(ServerRequest request, PathPattern pattern) {
			PathContainer pathContainer = request.requestPath().pathWithinApplication();
			boolean pathMatch = pattern.matches(pathContainer);
			traceMatch("Pattern", pattern.getPatternString(), request.path(), pathMatch);
			if (pathMatch) {
				request.attributes().put(RouterFunctions.MATCHING_PATTERN_ATTRIBUTE, pattern);
			}
			return pathMatch;
		}

		private static void traceMatch(String prefix, Object desired, @Nullable Object actual, boolean match) {
			if (logger.isTraceEnabled()) {
				logger.trace(String.format("%s \"%s\" %s against value \"%s\"",
						prefix, desired, match ? "matches" : "does not match", actual));
			}
		}
	}

}

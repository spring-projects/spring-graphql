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

package org.springframework.graphql.server.webflux;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * {@link RequestPredicate} implementations tailored for GraphQL reactive endpoints.
 *
 * @author Brian Clozel
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
		return new GraphQlHttpRequestPredicate(path, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL_RESPONSE);
	}

	/**
	 * Create a {@link RequestPredicate predicate} that matches GraphQL SSE over HTTP requests for the configured path.
	 * @param path the path on which the GraphQL SSE endpoint is mapped
	 * @see GraphQlSseHandler
	 */
	public static RequestPredicate graphQlSse(String path) {
		return new GraphQlHttpRequestPredicate(path, MediaType.TEXT_EVENT_STREAM);
	}

	private static class GraphQlHttpRequestPredicate implements RequestPredicate {

		private final PathPattern pattern;

		private final List<MediaType> acceptedMediaTypes;


		GraphQlHttpRequestPredicate(String path, MediaType... accepted) {
			Assert.notNull(path, "'path' must not be null");
			Assert.notEmpty(accepted, "'accepted' must not be empty");
			PathPatternParser parser = PathPatternParser.defaultInstance;
			path = parser.initFullPathPattern(path);
			this.pattern = parser.parse(path);
			this.acceptedMediaTypes = Arrays.asList(accepted);
		}

		@Override
		public boolean test(ServerRequest request) {
			return methodMatch(request, HttpMethod.POST)
					&& contentTypeMatch(request, MediaType.APPLICATION_JSON)
					&& acceptMatch(request, this.acceptedMediaTypes)
					&& pathMatch(request, this.pattern);
		}

		private static boolean methodMatch(ServerRequest request, HttpMethod expected) {
			HttpMethod actual = resolveMethod(request);
			boolean methodMatch = expected.equals(actual);
			traceMatch("Method", expected, actual, methodMatch);
			return methodMatch;
		}

		private static HttpMethod resolveMethod(ServerRequest request) {
			if (CorsUtils.isPreFlightRequest(request.exchange().getRequest())) {
				String accessControlRequestMethod =
						request.headers().firstHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
				if (accessControlRequestMethod != null) {
					return HttpMethod.valueOf(accessControlRequestMethod);
				}
			}
			return request.method();
		}

		private static boolean contentTypeMatch(ServerRequest request, MediaType expected) {
			if (CorsUtils.isPreFlightRequest(request.exchange().getRequest())) {
				return true;
			}
			ServerRequest.Headers headers = request.headers();
			MediaType actual = headers.contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
			boolean contentTypeMatch = expected.includes(actual);
			traceMatch("Content-Type", expected, actual, contentTypeMatch);
			return contentTypeMatch;
		}

		private static boolean acceptMatch(ServerRequest request, List<MediaType> expected) {
			if (CorsUtils.isPreFlightRequest(request.exchange().getRequest())) {
				return true;
			}
			ServerRequest.Headers headers = request.headers();
			List<MediaType> acceptedMediaTypes = acceptedMediaTypes(headers);
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

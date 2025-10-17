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

package org.springframework.graphql.server.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;

/**
 * Interceptor that copies HTTP request headers to the GraphQL context to make
 * them available to data fetchers such as annotated controllers, which can use
 * {@link org.springframework.graphql.data.method.annotation.ContextValue @ContextValue}
 * method parameters to access the headers as context values.
 *
 * <p>User {@link #builder()} to build an instance, and specify headers of
 * interest that should be copied to the GraphQL context.
 *
 * @author Rossen Stoyanchev
 * @since 2.0.0
 */
public final class HttpRequestHeaderInterceptor implements WebGraphQlInterceptor {

	private final List<BiConsumer<HttpHeaders, Map<String, Object>>> mappers;


	private HttpRequestHeaderInterceptor(List<BiConsumer<HttpHeaders, Map<String, Object>>> mappers) {
		this.mappers = new ArrayList<>(mappers);
	}


	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		request.configureExecutionInput((executionInput, builder) -> {
			HttpHeaders headers = request.getHeaders();
			Map<String, Object> target = new HashMap<>(this.mappers.size());
			this.mappers.forEach((mapper) -> mapper.accept(headers, target));
			builder.graphQLContext(target);
			return builder.build();
		});
		return chain.next(request);
	}


	/**
	 * Return a builder to create an {@link HttpRequestHeaderInterceptor}.
	 */
	public static Builder builder() {
		return new DefaultBuilder();
	}


	/**
	 * Builder for {@link HttpRequestHeaderInterceptor}.
	 */
	public interface Builder {

		/**
		 * Add names of HTTP headers to copy to the GraphQL context, using keys
		 * identical to the header names. Only the first value is copied.
		 * @param headerName the name(s) of header(s) to copy
		 */
		Builder mapHeader(String... headerName);

		/**
		 * Add a mapping between an HTTP header name and the key under which it
		 * should appear in the GraphQL context. Only the first value is copied.
		 * @param headerName the name of a header to copy
		 * @param contextKey the key to map to in the GraphQL context
		 */
		Builder mapHeaderToKey(String headerName, String contextKey);

		/**
		 * Add names of HTTP headers to copy to the GraphQL context, using keys
		 * identical to the header names. All values are copied as a List.
		 * @param headerName the name(s) of header(s) to copy
		 */
		Builder mapMultiValueHeader(String... headerName);

		/**
		 * Add a mapping between an HTTP header name and the key under which it
		 * should appear in the GraphQL context. All values are copied as a List.
		 * @param headerName the name of a header to copy
		 * @param contextKey the key to map to in the GraphQL context
		 */
		Builder mapMultiValueHeaderToKey(String headerName, String contextKey);

		/**
		 * Create the interceptor instance.
		 */
		HttpRequestHeaderInterceptor build();
	}


	/**
	 * Default implementation of {@link Builder}.
	 */
	private static final class DefaultBuilder implements Builder {

		private final List<BiConsumer<HttpHeaders, Map<String, Object>>> mappers = new ArrayList<>();

		@Override
		public DefaultBuilder mapHeader(String... headers) {
			for (String header : headers) {
				initMapper(header, null);
			}
			return this;
		}

		@Override
		public DefaultBuilder mapHeaderToKey(String header, String contextKey) {
			initMapper(header, contextKey);
			return this;
		}

		private void initMapper(String header, @Nullable String key) {
			this.mappers.add((headers, target) -> {
				Object value = headers.getFirst(header);
				if (value != null) {
					target.put((key != null) ? key : header, value);
				}
			});
		}

		@Override
		public DefaultBuilder mapMultiValueHeader(String... headers) {
			for (String header : headers) {
				initMultiValueMapper(header, null);
			}
			return this;
		}

		@Override
		public DefaultBuilder mapMultiValueHeaderToKey(String header, String contextKey) {
			initMultiValueMapper(header, contextKey);
			return this;
		}

		private void initMultiValueMapper(String header, @Nullable String key) {
			this.mappers.add((headers, target) -> {
				List<?> list = headers.getValuesAsList(header);
				if (!ObjectUtils.isEmpty(list)) {
					target.put((key != null) ? key : header, list);
				}
			});
		}

		@Override
		public HttpRequestHeaderInterceptor build() {
			return new HttpRequestHeaderInterceptor(this.mappers);
		}
	}

}

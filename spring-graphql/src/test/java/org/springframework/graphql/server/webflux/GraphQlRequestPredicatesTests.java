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


import java.util.Collections;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlRequestPredicates}.
 *
 * @author Brian Clozel
 */
class GraphQlRequestPredicatesTests {

	@Nested
	class HttpPredicatesTests {

		RequestPredicate httpPredicate = GraphQlRequestPredicates.graphQlHttp("/graphql");

		@Test
		void shouldAcceptGraphQlHttpRequest() {
			ServerWebExchange exchange = createMatchingHttpExchange();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(httpPredicate.test(serverRequest)).isTrue();
		}

		@Test
		void shouldAcceptCorsRequest() {
			ServerWebExchange exchange = createMatchingHttpExchange()
					.mutate().request(req -> req.method(HttpMethod.OPTIONS).header("Origin", "https://example.org")
							.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")).build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(httpPredicate.test(serverRequest)).isTrue();
		}

		@Test
		void shouldRejectRequestWithGetMethod() {
			ServerWebExchange exchange = createMatchingHttpExchange()
					.mutate().request(req -> req.method(HttpMethod.GET)).build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(httpPredicate.test(serverRequest)).isFalse();
		}

		@Test
		void shouldRejectRequestWithDifferentPath() {
			ServerWebExchange exchange = createMatchingHttpExchange()
					.mutate().request(req -> req.path("/invalid")).build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(httpPredicate.test(serverRequest)).isFalse();
		}

		@Test
		void shouldMapApplicationGraphQlRequestContent() {
			ServerWebExchange exchange = createMatchingHttpExchange()
					.mutate().request(builder -> builder.headers(headers -> {
						MediaType contentType = MediaType.parseMediaType("application/graphql");
						headers.setContentType(contentType);
					}))
					.build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(httpPredicate.test(serverRequest)).isTrue();
		}

		@Test
		void shouldRejectRequestWithDifferentContentType() {
			ServerWebExchange exchange = createMatchingHttpExchange()
					.mutate().request(req -> req.headers(headers -> headers.setContentType(MediaType.TEXT_HTML)))
					.build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(httpPredicate.test(serverRequest)).isFalse();
		}

		@Test
		void shouldRejectRequestWithIncompatibleAccept() {
			ServerWebExchange exchange = createMatchingHttpExchange()
					.mutate().request(req -> req.headers(headers -> headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML))))
					.build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(httpPredicate.test(serverRequest)).isFalse();
		}

		private MockServerWebExchange createMatchingHttpExchange() {
			MockServerHttpRequest request = MockServerHttpRequest.post("/graphql")
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL_RESPONSE)
					.build();
			return MockServerWebExchange.from(request);
		}

	}

	@Nested
	class SsePredicatesTests {

		RequestPredicate ssePredicate = GraphQlRequestPredicates.graphQlSse("/graphql");

		@Test
		void shouldAcceptGraphQlSseRequest() {
			ServerWebExchange exchange = createMatchingSseExchange();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(ssePredicate.test(serverRequest)).isTrue();
		}

		@Test
		void shouldAcceptCorsRequest() {
			ServerWebExchange exchange = createMatchingSseExchange()
					.mutate().request(req -> req.method(HttpMethod.OPTIONS).header("Origin", "https://example.org")
							.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")).build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(ssePredicate.test(serverRequest)).isTrue();
		}

		@Test
		void shouldRejectRequestWithGetMethod() {
			ServerWebExchange exchange = createMatchingSseExchange()
					.mutate().request(req -> req.method(HttpMethod.GET)).build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(ssePredicate.test(serverRequest)).isFalse();
		}

		@Test
		void shouldRejectRequestWithDifferentPath() {
			ServerWebExchange exchange = createMatchingSseExchange()
					.mutate().request(req -> req.path("/invalid")).build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(ssePredicate.test(serverRequest)).isFalse();
		}

		@Test
		void shouldRejectRequestWithDifferentContentType() {
			ServerWebExchange exchange = createMatchingSseExchange()
					.mutate().request(req -> req.headers(headers -> headers.setContentType(MediaType.TEXT_HTML)))
					.build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(ssePredicate.test(serverRequest)).isFalse();
		}

		@Test
		void shouldRejectRequestWithIncompatibleAccept() {
			ServerWebExchange exchange = createMatchingSseExchange()
					.mutate().request(req -> req.headers(headers -> headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML))))
					.build();
			ServerRequest serverRequest = ServerRequest.create(exchange, Collections.emptyList());
			assertThat(ssePredicate.test(serverRequest)).isFalse();
		}

		private MockServerWebExchange createMatchingSseExchange() {
			MockServerHttpRequest request = MockServerHttpRequest.post("/graphql")
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.build();
			return MockServerWebExchange.from(request);
		}
	}

}

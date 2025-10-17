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

import java.net.URI;
import java.util.Collections;
import java.util.List;

import graphql.GraphQLContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HttpRequestHeaderInterceptor}.
 * @author Rossen Stoyanchev
 */
class HttpRequestHeaderInterceptorTests {

	@Test
	void map() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("h1", "v1");
		headers.put("h2", List.of("v2A", "v2B"));
		headers.add("h3", "v3");
		headers.put("h4", List.of("v4A", "v4B"));
		headers.put("h5", List.of("v5A", "v5B"));

		HttpRequestHeaderInterceptor interceptor = HttpRequestHeaderInterceptor.builder()
				.mapHeader("h1", "h2")
				.mapHeaderToKey("h3", "k3")
				.mapMultiValueHeader("h4")
				.mapMultiValueHeaderToKey("h5", "k5")
				.build();

		WebGraphQlRequest request = new WebGraphQlRequest(
				URI.create("/"), headers, null, null, Collections.emptyMap(),
				new DefaultGraphQlRequest("{ q }"), "id", null);

		interceptor.intercept(request, new TestChain()).block();

		GraphQLContext context = request.toExecutionInput().getGraphQLContext();

		assertThat(context.<String>get("h1")).isEqualTo("v1");
		assertThat(context.<String>get("h2")).isEqualTo("v2A");
		assertThat(context.<String>get("k3")).isEqualTo("v3");
		assertThat(context.<List<String>>get("h4")).containsExactly("v4A", "v4B");
		assertThat(context.<List<String>>get("k5")).containsExactly("v5A", "v5B");
	}


	@SuppressWarnings("NullableProblems")
	private static final class TestChain implements WebGraphQlInterceptor.Chain {

		@Override
		public Mono<WebGraphQlResponse> next(WebGraphQlRequest request) {
			return Mono.empty();
		}
	}

}

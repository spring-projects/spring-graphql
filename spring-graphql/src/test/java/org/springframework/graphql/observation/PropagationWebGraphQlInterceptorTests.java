/*
 * Copyright 2020-2023 the original author or authors.
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

package org.springframework.graphql.observation;


import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import graphql.GraphQLContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PropagationWebGraphQlInterceptor}.
 *
 * @author Brian Clozel
 */
class PropagationWebGraphQlInterceptorTests {

	PropagationWebGraphQlInterceptor interceptor = new PropagationWebGraphQlInterceptor(new TestPropagator());

	@Test
	void rejectsNullPropagator() {
		assertThatThrownBy(() -> new PropagationWebGraphQlInterceptor(null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("propagator should not be null");
	}

	@Test
	void copiesPropagationHeadersWhenPresent() {
		Map<String, String> tracingHeaders = Map.of("X-Test-TraceId", "traceId", "baggage", "project=spring");
		Map<String, String> httpHeaders = new HashMap<>();
		httpHeaders.put("Accept", "application/graphql+json");
		httpHeaders.putAll(tracingHeaders);
		WebGraphQlRequest webRequest = createRequest(httpHeaders);

		WebGraphQlHandler handler = WebGraphQlHandler.builder(request -> {
			GraphQLContext context = request.toExecutionInput().getGraphQLContext();
			assertThatContextContains(context, tracingHeaders);
			return emptyExecutionResult(request);
		}).interceptor(this.interceptor).build();
		handler.handleRequest(webRequest).block();
	}

	private void assertThatContextContains(GraphQLContext context, Map<String, String> tracingHeaders) {
		tracingHeaders.forEach((key, value) -> {
			String actual = context.get(key);
			assertThat(actual).isEqualTo(value);
		});
	}

	WebGraphQlRequest createRequest(Map<String, String> headers) {
		HttpHeaders httpHeaders = new HttpHeaders();
		headers.forEach(httpHeaders::set);
		return new WebGraphQlRequest(
				URI.create("https://example.org/graphql"), httpHeaders, null,
				Map.of("query", "{ notUsed }"), "1", null);
	}

	private Mono<ExecutionGraphQlResponse> emptyExecutionResult(ExecutionGraphQlRequest request) {
		return Mono.just(new DefaultExecutionGraphQlResponse(
				ExecutionInput.newExecutionInput("{}").build(),
				ExecutionResultImpl.newExecutionResult().build()));
	}


	static class TestPropagator implements Propagator {

		@Override
		public List<String> fields() {
			return List.of("X-Test-TraceId", "baggage");
		}

		@Override
		public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {

		}

		@Override
		public <C> Span.Builder extract(C carrier, Getter<C> getter) {
			return null;
		}
	}

}
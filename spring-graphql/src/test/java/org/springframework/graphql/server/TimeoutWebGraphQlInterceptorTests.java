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

package org.springframework.graphql.server;


import java.net.URI;
import java.time.Duration;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TimeoutWebGraphQlInterceptor}.
 */
class TimeoutWebGraphQlInterceptorTests {

	@Test
	void shouldRespondWhenTimeoutNotExceeded() {
		TimeoutWebGraphQlInterceptor interceptor = new TimeoutWebGraphQlInterceptor(Duration.ofSeconds(3));
		TestChain interceptorChain = new TestChain(Duration.ofMillis(200));
		Mono<WebGraphQlResponse> response = interceptor.intercept(createRequest(), interceptorChain);

		StepVerifier.create(response).expectNextCount(1).expectComplete().verify();
		assertThat(interceptorChain.cancelled).isFalse();
	}

	@Test
	void shouldTimeoutWithDefaultStatus() {
		TimeoutWebGraphQlInterceptor interceptor = new TimeoutWebGraphQlInterceptor(Duration.ofMillis(200));
		TestChain interceptorChain = new TestChain(Duration.ofSeconds(1));
		Mono<WebGraphQlResponse> response = interceptor.intercept(createRequest(), interceptorChain);

		StepVerifier.create(response).expectErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(ResponseStatusException.class);
			ResponseStatusException responseStatusException = (ResponseStatusException) error;
			assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
		}).verify();
		assertThat(interceptorChain.cancelled).isTrue();
	}

	@Test
	void shouldTimeoutWithCustomStatus() {
		TimeoutWebGraphQlInterceptor interceptor = new TimeoutWebGraphQlInterceptor(Duration.ofMillis(200), HttpStatus.GATEWAY_TIMEOUT);
		TestChain interceptorChain = new TestChain(Duration.ofSeconds(1));
		Mono<WebGraphQlResponse> response = interceptor.intercept(createRequest(), interceptorChain);

		StepVerifier.create(response).expectErrorSatisfies(error -> {
			assertThat(error).isInstanceOf(ResponseStatusException.class);
			ResponseStatusException responseStatusException = (ResponseStatusException) error;
			assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
		}).verify();
		assertThat(interceptorChain.cancelled).isTrue();
	}

	WebGraphQlRequest createRequest() {
		return new WebGraphQlRequest(URI.create("https://localhost/graphql"), new HttpHeaders(),
				null, null, Map.of(), new DefaultGraphQlRequest("{ greeting }"), "id", null);
	}

	class TestChain implements WebGraphQlInterceptor.Chain {

		private Duration delay;

		boolean cancelled;

		public TestChain(Duration delay) {
			this.delay = delay;
		}

		@Override
		public Mono<WebGraphQlResponse> next(WebGraphQlRequest request) {
			ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("{ greeting }").build();
			ExecutionResult executionResult = ExecutionResult.newExecutionResult().data("Hello World").build();
			ExecutionGraphQlResponse response = new DefaultExecutionGraphQlResponse(executionInput, executionResult);
			return Mono.just(new WebGraphQlResponse(response))
					.delayElement(this.delay)
					.doOnCancel(() -> this.cancelled = true);
		}
	}

}

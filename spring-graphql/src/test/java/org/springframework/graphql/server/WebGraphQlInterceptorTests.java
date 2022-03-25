/*
 * Copyright 2002-2022 the original author or authors.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for a {@link WebGraphQlInterceptor} chain.
 */
public class WebGraphQlInterceptorTests {

	private static final WebGraphQlRequest webRequest = new WebGraphQlRequest(
			URI.create("http://abc.org"), new HttpHeaders(), Collections.singletonMap("query", "{ notUsed }"), "1", null);

	@Test
	void interceptorOrder() {
		StringBuilder sb = new StringBuilder();

		WebGraphQlHandler handler = WebGraphQlHandler.builder(this::emptyExecutionResult)
				.interceptors(Arrays.asList(
						new OrderInterceptor(1, sb),
						new OrderInterceptor(2, sb),
						new OrderInterceptor(3, sb)))
				.build();

		handler.handleRequest(webRequest).block();
		assertThat(sb.toString()).isEqualTo(":pre1:pre2:pre3:post3:post2:post1");
	}

	@Test
	void responseHeader() {
		WebGraphQlHandler handler = WebGraphQlHandler.builder(this::emptyExecutionResult)
				.interceptor((input, next) -> next.next(input)
						.doOnNext(response -> {
							HttpHeaders httpHeaders = response.getResponseHeaders();
							httpHeaders.add("testHeader", "testValue");
						}))
				.build();

		HttpHeaders headers = handler.handleRequest(webRequest).block().getResponseHeaders();

		assertThat(headers.get("testHeader")).containsExactly("testValue");
	}

	@Test
	void executionInputCustomization() {
		AtomicReference<String> actualName = new AtomicReference<>();

		WebGraphQlHandler handler = WebGraphQlHandler
				.builder((request) -> {
					actualName.set(request.toExecutionInput().getOperationName());
					return emptyExecutionResult(request);
				})
				.interceptor((request, chain) -> {
					request.configureExecutionInput((input, builder) -> builder.operationName("testOp").build());
					return chain.next(request);
				})
				.build();

		handler.handleRequest(webRequest).block();

		assertThat(actualName.get()).isEqualTo("testOp");
	}

	private Mono<ExecutionGraphQlResponse> emptyExecutionResult(ExecutionGraphQlRequest request) {
		return Mono.just(new DefaultExecutionGraphQlResponse(
				ExecutionInput.newExecutionInput("{}").build(),
				ExecutionResultImpl.newExecutionResult().build()));
	}

	private static class OrderInterceptor implements WebGraphQlInterceptor {

		private final StringBuilder sb;

		private final int order;

		OrderInterceptor(int order, StringBuilder sb) {
			this.sb = sb;
			this.order = order;
		}

		@Override
		public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
			this.sb.append(":pre").append(this.order);
			return chain.next(request)
					.map((response) -> {
						this.sb.append(":post").append(this.order);
						return response;
					})
					.subscribeOn(Schedulers.boundedElastic());
		}

	}

}

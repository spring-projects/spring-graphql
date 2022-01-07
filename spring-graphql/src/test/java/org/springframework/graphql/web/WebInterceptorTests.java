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

package org.springframework.graphql.web;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import graphql.ExecutionResultImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.RequestOutput;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for a {@link WebInterceptor} chain.
 */
public class WebInterceptorTests {

	private static final WebInput webInput = new WebInput(
			URI.create("http://abc.org"), new HttpHeaders(), Collections.singletonMap("query", "{ notUsed }"),
			null, "1");

	@Test
	void interceptorOrder() {
		StringBuilder output = new StringBuilder();

		WebGraphQlHandler handler = WebGraphQlHandler.builder(this::emptyExecutionResult)
				.interceptors(Arrays.asList(
						new OrderInterceptor(1, output),
						new OrderInterceptor(2, output),
						new OrderInterceptor(3, output)))
				.build();

		handler.handleRequest(webInput).block();
		assertThat(output.toString()).isEqualTo(":pre1:pre2:pre3:post3:post2:post1");
	}

	@Test
	void responseHeader() {
		Function<WebOutput, WebOutput> headerFunction = (output) ->
				output.transform((builder) -> builder.responseHeader("testHeader", "testValue"));

		WebGraphQlHandler handler = WebGraphQlHandler.builder(this::emptyExecutionResult)
				.interceptor((input, next) -> next.next(input).map(headerFunction))
				.build();

		HttpHeaders headers = handler.handleRequest(webInput).block().getResponseHeaders();

		assertThat(headers.get("testHeader")).containsExactly("testValue");
	}

	@Test
	void executionInputCustomization() {
		AtomicReference<String> actualName = new AtomicReference<>();

		WebGraphQlHandler handler = WebGraphQlHandler
				.builder((input) -> {
					actualName.set(input.toExecutionInput().getOperationName());
					return emptyExecutionResult(input);
				})
				.interceptor((webInput, next) -> {
					webInput.configureExecutionInput((input, builder) -> builder.operationName("testOp").build());
					return next.next(webInput);
				})
				.build();

		handler.handleRequest(webInput).block();

		assertThat(actualName.get()).isEqualTo("testOp");
	}

	private Mono<RequestOutput> emptyExecutionResult(RequestInput input) {
		return Mono.just(new RequestOutput(input, ExecutionResultImpl.newExecutionResult().build()));
	}

	private static class OrderInterceptor implements WebInterceptor {

		private final StringBuilder output;

		private final int order;

		OrderInterceptor(int order, StringBuilder output) {
			this.output = output;
			this.order = order;
		}

		@Override
		public Mono<WebOutput> intercept(WebInput input, WebInterceptorChain chain) {
			this.output.append(":pre").append(this.order);
			return chain.next(input)
					.map((output) -> {
						this.output.append(":post").append(this.order);
						return output;
					})
					.subscribeOn(Schedulers.boundedElastic());
		}

	}

}

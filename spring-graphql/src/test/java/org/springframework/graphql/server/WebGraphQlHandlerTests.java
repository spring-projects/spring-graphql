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
import java.time.Duration;
import java.util.Collections;

import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetcher;
import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestThreadLocalAccessor;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebGraphQlHandler}, common to both HTTP and WebSocket.
 */
public class WebGraphQlHandlerTests {

	private static final WebGraphQlRequest webInput = new WebGraphQlRequest(
			URI.create("https://abc.org"), new HttpHeaders(), Collections.singletonMap("query", "{ greeting }"), "1", null);


	private final GraphQlSetup graphQlSetup = GraphQlSetup.schemaContent("type Query { greeting: String }");

	private final DataFetcher<Object> errorDataFetcher = env -> {
		throw new IllegalArgumentException("Invalid greeting");
	};


	@Test
	void reactorContextPropagation() {
		DataFetcher<Object> dataFetcher = (env) -> Mono.deferContextual((context) -> {
			Object name = context.get("name");
			return Mono.delay(Duration.ofMillis(50)).map((aLong) -> "Hello " + name);
		});

		Mono<WebGraphQlResponse> responseMono =
				this.graphQlSetup.queryFetcher("greeting", dataFetcher).toWebGraphQlHandler()
						.handleRequest(webInput)
						.contextWrite((context) -> context.put("name", "007"));

		String greeting = ResponseHelper.forResponse(responseMono).toEntity("greeting", String.class);
		assertThat(greeting).isEqualTo("Hello 007");
	}

	@Test
	void reactorContextPropagationToExceptionResolver() {
		DataFetcherExceptionResolver exceptionResolver =
				(ex, env) -> Mono.deferContextual((view) -> Mono.just(Collections.singletonList(
						GraphqlErrorBuilder.newError(env)
								.message("Resolved error: " + ex.getMessage() + ", name=" + view.get("name"))
								.errorType(ErrorType.BAD_REQUEST)
								.build())));

		Mono<WebGraphQlResponse> responseMono = this.graphQlSetup.queryFetcher("greeting", this.errorDataFetcher)
				.exceptionResolver(exceptionResolver)
				.toWebGraphQlHandler()
				.handleRequest(webInput)
				.contextWrite((cxt) -> cxt.put("name", "007"));

		ResponseHelper response = ResponseHelper.forResponse(responseMono);
		assertThat(response.errorCount()).isEqualTo(1);
		assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting, name=007");

		String greeting = response.rawValue("greeting");
		assertThat(greeting).isNull();
	}

	@Test
	void threadLocalContextPropagation() {
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("007");
		ContextRegistry.getInstance().registerThreadLocalAccessor(new TestThreadLocalAccessor<>(threadLocal));
		try {
			Mono<WebGraphQlResponse> responseMono = this.graphQlSetup
					.queryFetcher("greeting", env -> "Hello " + threadLocal.get())
					.interceptor((input, next) -> Mono.delay(Duration.ofMillis(10)).flatMap((aLong) -> next.next(input)))
					.toWebGraphQlHandler()
					.handleRequest(webInput);

			String greeting = ResponseHelper.forResponse(responseMono).toEntity("greeting", String.class);
			assertThat(greeting).isEqualTo("Hello 007");
		}
		finally {
			threadLocal.remove();
		}
	}

	@Test
	void threadLocalContextPropagationToExceptionResolver() {
		ThreadLocal<String> threadLocal = new ThreadLocal<>();
		threadLocal.set("007");
		ContextRegistry.getInstance().registerThreadLocalAccessor(new TestThreadLocalAccessor<>(threadLocal));
		try {
			DataFetcherExceptionResolverAdapter exceptionResolver =
					DataFetcherExceptionResolver.forSingleError((ex, env) ->
							GraphqlErrorBuilder.newError(env)
									.message("Resolved error: " + ex.getMessage() + ", name=" + threadLocal.get())
									.errorType(ErrorType.BAD_REQUEST).build());
			exceptionResolver.setThreadLocalContextAware(true);

			Mono<WebGraphQlResponse> responseMono = this.graphQlSetup.queryFetcher("greeting", this.errorDataFetcher)
					.exceptionResolver(exceptionResolver)
					.interceptor((input, next) -> Mono.delay(Duration.ofMillis(10)).flatMap((aLong) -> next.next(input)))
					.toWebGraphQlHandler()
					.handleRequest(webInput);

			ResponseHelper response = ResponseHelper.forResponse(responseMono);
			assertThat(response.errorCount()).isEqualTo(1);
			assertThat(response.error(0).message()).isEqualTo("Resolved error: Invalid greeting, name=007");
		}
		finally {
			threadLocal.remove();
		}
	}

}

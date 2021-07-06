/*
 * Copyright 2002-2021 the original author or authors.
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
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.GraphQlTestUtils;
import org.springframework.graphql.TestGraphQlSource;
import org.springframework.graphql.TestThreadLocalAccessor;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebGraphQlHandler}, common to both HTTP and WebSocket.
 */
public class WebGraphQlHandlerTests {

	private static final WebInput webInput = new WebInput(
			URI.create("http://abc.org"), new HttpHeaders(), Collections.singletonMap("query", "{ greeting }"), "1");

	@Test
	void reactorContextPropagation() {
		GraphQL graphQl = GraphQlTestUtils.initGraphQl(
				"type Query { greeting: String }", "Query", "greeting",
				(env) -> Mono.deferContextual((context) -> {
					Object name = context.get("name");
					return Mono.delay(Duration.ofMillis(50)).map((aLong) -> "Hello " + name);
				}));

		GraphQlService service = new ExecutionGraphQlService(new TestGraphQlSource(graphQl));
		WebGraphQlHandler handler = WebGraphQlHandler.builder(service).build();

		WebOutput webOutput = handler.handle(webInput).contextWrite((context) -> context.put("name", "007")).block();

		Map<String, Object> data = webOutput.getData();
		assertThat(data).hasSize(1).containsEntry("greeting", "Hello 007");
	}

	@Test
	void reactorContextPropagationToExceptionResolver() {
		GraphQL graphQl = GraphQlTestUtils.initGraphQl("type Query { greeting: String }", "Query", "greeting",
				(env) -> {
					throw new IllegalArgumentException("Invalid greeting");
				},
				(ex, env) -> Mono.deferContextual((view) -> Mono.just(Collections.singletonList(
						GraphqlErrorBuilder
						.newError(env).message("Resolved error: " + ex.getMessage() + ", name=" + view.get("name"))
						.errorType(ErrorType.BAD_REQUEST).build()))));

		GraphQlService service = new ExecutionGraphQlService(new TestGraphQlSource(graphQl));
		WebGraphQlHandler handler = WebGraphQlHandler.builder(service).build();

		WebOutput webOutput = handler.handle(webInput).contextWrite((context) -> context.put("name", "007")).block();

		Map<String, Object> data = webOutput.getData();
		assertThat(data).hasSize(1).containsEntry("greeting", null);

		List<GraphQLError> errors = webOutput.getErrors();
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getMessage()).isEqualTo("Resolved error: Invalid greeting, name=007");
	}

	@Test
	void threadLocalContextPropagation() {
		ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		nameThreadLocal.set("007");
		TestThreadLocalAccessor<String> threadLocalAccessor = new TestThreadLocalAccessor<>(nameThreadLocal);
		try {
			GraphQL graphQl = GraphQlTestUtils.initGraphQl(
					"type Query { greeting: String }", "Query", "greeting",
					(env) -> "Hello " + nameThreadLocal.get());

			GraphQlService service = new ExecutionGraphQlService(new TestGraphQlSource(graphQl));

			WebGraphQlHandler handler = WebGraphQlHandler.builder(service)
					.interceptor((input, next) -> Mono.delay(Duration.ofMillis(10)).flatMap((aLong) -> next.handle(input)))
					.threadLocalAccessor(threadLocalAccessor)
					.build();

			Map<String, Object> data = handler.handle(webInput).block().getData();

			assertThat(data).hasSize(1).containsEntry("greeting", "Hello 007");
		}
		finally {
			nameThreadLocal.remove();
		}
	}

	@Test
	void threadLocalContextPropagationToExceptionResolver() {
		ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		nameThreadLocal.set("007");
		TestThreadLocalAccessor<String> threadLocalAccessor = new TestThreadLocalAccessor<>(nameThreadLocal);
		try {
			GraphQL graphQl = GraphQlTestUtils.initGraphQl("type Query { greeting: String }", "Query", "greeting",
					(env) -> {
						throw new IllegalArgumentException("Invalid greeting");
					},
					threadLocalContextAwareExceptionResolver((ex, env) ->
							GraphqlErrorBuilder.newError(env)
									.message("Resolved error: " + ex.getMessage() + ", name=" + nameThreadLocal.get())
									.errorType(ErrorType.BAD_REQUEST).build()));

			GraphQlService service = new ExecutionGraphQlService(new TestGraphQlSource(graphQl));

			WebGraphQlHandler handler = WebGraphQlHandler.builder(service)
					.interceptor((input, next) -> Mono.delay(Duration.ofMillis(10)).flatMap((aLong) -> next.handle(input)))
					.threadLocalAccessor(threadLocalAccessor)
					.build();

			WebOutput webOutput = handler.handle(webInput).block();

			List<GraphQLError> errors = webOutput.getErrors();
			assertThat(errors.get(0).getMessage()).isEqualTo("Resolved error: Invalid greeting, name=007");
		}
		finally {
			nameThreadLocal.remove();
		}
	}

	private static DataFetcherExceptionResolver threadLocalContextAwareExceptionResolver(
			BiFunction<Throwable, DataFetchingEnvironment, GraphQLError> resolver) {

		DataFetcherExceptionResolverAdapter adapter = new DataFetcherExceptionResolverAdapter() {

			@Override
			protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
				return resolver.apply(ex, env);
			}
		};
		adapter.setThreadLocalContextAware(true);
		return adapter;
	}

}

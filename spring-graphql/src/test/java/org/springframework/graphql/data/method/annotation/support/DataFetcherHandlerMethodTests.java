/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.data.method.annotation.support;


import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataFetcherHandlerMethod}.
 *
 * @author Rossen Stoyanchev
 */
class DataFetcherHandlerMethodTests {

	@Test
	void annotatedMethodsOnInterface() {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
		resolvers.addResolver(new ArgumentMethodArgumentResolver(new GraphQlArgumentBinder()));

		DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
				handlerMethodFor(new TestController(), "hello"), resolvers, null, null, false, false);

		Object result = handlerMethod.invoke(
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
						.arguments(Collections.singletonMap("name", "Neil"))
						.build());

		assertThat(result).isEqualTo("Hello, Neil");
	}

	@Test
	void asyncInvocation() throws Exception {
		testAsyncInvocation("handleSync", false, true, "A");
	}

	@Test
	void asyncInvocationWithCallableReturnValue() throws Exception {
		testAsyncInvocation("handleAndReturnCallable", false, false, "A");
	}

	@Test
	void asyncInvocationWithCallableReturnValueError() throws Exception {
		testAsyncInvocation("handleAndReturnCallable", true, false, "simulated exception");
	}

	private static void testAsyncInvocation(
			String methodName, boolean raiseError, boolean invokeAsync, String expected) throws Exception {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
		resolvers.addResolver(new ArgumentMethodArgumentResolver(new GraphQlArgumentBinder()));

		DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
				handlerMethodFor(new TestController(), methodName), resolvers, null,
				new SimpleAsyncTaskExecutor(), invokeAsync, false);

		DataFetchingEnvironment environment = DataFetchingEnvironmentImpl
				.newDataFetchingEnvironment()
				.arguments(Map.of("raiseError", raiseError))  // gh-973
				.graphQLContext(GraphQLContext.newContext().build())
				.build();

		Object result = handlerMethod.invoke(environment);

		assertThat(result).isInstanceOf(CompletableFuture.class);
		CompletableFuture<String> future = (CompletableFuture<String>) result;
		if (raiseError) {
			future = future.handle((s, ex) -> ex.getMessage());
		}
		assertThat(future.get()).isEqualTo(expected);
	}

	@Test
	void completableFutureReturnValue() {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
		resolvers.addResolver(new AuthenticationPrincipalArgumentResolver((beanName, context) -> null));

		DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
				handlerMethodFor(new TestController(), "handleAndReturnFuture"), resolvers,
				null, null, false, false);

		SecurityContextHolder.setContext(new SecurityContextImpl(new TestingAuthenticationToken("usr", "pwd")));
		try {
			Object result = handlerMethod.invoke(
					DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build());

			assertThat(result).isInstanceOf(Mono.class);
			assertThat(((Mono<String>) result).block()).isEqualTo("B");
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private static HandlerMethod handlerMethodFor(Object controller, String methodName) {
		Method method = ClassUtils.getMethod(controller.getClass(), methodName, (Class<?>[]) null);
		return new HandlerMethod(controller, method);
	}


	interface TestInterface {

		@QueryMapping
		String hello(@Argument String name);

	}

	@SuppressWarnings("unused")
	private static class TestController implements TestInterface {

		@Override
		public String hello(String name) {
			return "Hello, " + name;
		}

		public String handleSync() {
			return "A";
		}

		public Callable<String> handleAndReturnCallable(@Argument boolean raiseError) {
			return () -> {
				if (raiseError) {
					throw new IllegalStateException("simulated exception");
				}
				return "A";
			};
		}

		public CompletableFuture<String> handleAndReturnFuture(@AuthenticationPrincipal User user) {
			return CompletableFuture.completedFuture("B");
		}

	}

}

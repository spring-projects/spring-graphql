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
package org.springframework.graphql.data.method.annotation.support;


import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.micrometer.context.ContextSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataFetcherHandlerMethod}.
 *
 * @author Rossen Stoyanchev
 */
public class DataFetcherHandlerMethodTests {

	@Test
	void annotatedMethodsOnInterface() {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
		resolvers.addResolver(new ArgumentMethodArgumentResolver(new GraphQlArgumentBinder()));

		DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
				handlerMethodFor(new TestController(), "hello"), resolvers, null, null, false);

		Object result = handlerMethod.invoke(
				DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
						.arguments(Collections.singletonMap("name", "Neil"))
						.build());

		assertThat(result).isEqualTo("Hello, Neil");
	}

	@Test
	void callableReturnValue() throws Exception {

		HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
		resolvers.addResolver(Mockito.mock(HandlerMethodArgumentResolver.class));

		DataFetcherHandlerMethod handlerMethod = new DataFetcherHandlerMethod(
				handlerMethodFor(new TestController(), "handleAndReturnCallable"), resolvers, null,
				new SimpleAsyncTaskExecutor(), false);

		DataFetchingEnvironment environment = DataFetchingEnvironmentImpl
				.newDataFetchingEnvironment()
				.graphQLContext(GraphQLContext.newContext().build())
				.build();

		Object result = handlerMethod.invoke(environment);

		assertThat(result).isInstanceOf(CompletableFuture.class);
		CompletableFuture<String> future = (CompletableFuture<String>) result;
		assertThat(future.get()).isEqualTo("A");
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

		@Nullable
		public Callable<String> handleAndReturnCallable() {
			return () -> "A";
		}

	}

}

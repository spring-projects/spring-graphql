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

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.LocalContextValue;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ContextValueMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
public class LocalContextValueMethodArgumentResolverTests {

	private static final Method method = ClassUtils.getMethod(
			LocalContextValueMethodArgumentResolverTests.class, "handle", (Class<?>[]) null);

	private final LocalContextValueMethodArgumentResolver resolver = new LocalContextValueMethodArgumentResolver();

	private final Book book = new Book();


	@Test
	void supportsParameter() {
		assertThat(this.resolver.supportsParameter(methodParam(0))).isFalse();
		assertThat(this.resolver.supportsParameter(methodParam(1))).isTrue();
	}

	@Test
	void resolve() {
		GraphQLContext context = GraphQLContext.newContext().of("localBook", this.book).build();
		Object actual = resolveValue(context, 1);

		assertThat(actual).isSameAs(this.book);
	}

	@Nullable
	private Object resolveValue(@Nullable GraphQLContext localContext, int index) {

		DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
				.localContext(localContext)
				.graphQLContext(GraphQLContext.newContext().build())
				.build();

		return this.resolver.resolveArgument(methodParam(index), environment);
	}

	private MethodParameter methodParam(int index) {
		MethodParameter methodParameter = new SynthesizingMethodParameter(method, index);
		methodParameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
		return methodParameter;
	}


	@SuppressWarnings("unused")
	public void handle(
			@ContextValue Book book,
			@LocalContextValue Book localBook) {
	}

}

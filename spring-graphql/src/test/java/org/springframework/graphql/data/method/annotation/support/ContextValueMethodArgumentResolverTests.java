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
package org.springframework.graphql.data.method.annotation.support;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.Test;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link ContextValueMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
public class ContextValueMethodArgumentResolverTests {

	private static final Method method = ClassUtils.getMethod(
			ContextValueMethodArgumentResolverTests.class, "handle", (Class<?>[]) null);

	private final ContextValueMethodArgumentResolver resolver = new ContextValueMethodArgumentResolver();

	private final Book book = new Book();


	@Test
	void supportsParameter() {
		assertThat(this.resolver.supportsParameter(methodParam(0))).isTrue();
		assertThat(this.resolver.supportsParameter(methodParam(1))).isTrue();
		assertThat(this.resolver.supportsParameter(methodParam(2))).isTrue();
		assertThat(this.resolver.supportsParameter(methodParam(3))).isTrue();
		assertThat(this.resolver.supportsParameter(methodParam(4))).isFalse();
	}

	@Test
	void resolve() {
		BiConsumer<String, Integer> tester = (key, index) -> {
			GraphQLContext context = GraphQLContext.newContext().of(key, this.book).build();
			Object actual = resolveValue(null, context, index);
			assertThat(actual).isSameAs(this.book);
		};
		tester.accept("book", 0);
		tester.accept("customKey", 1);
	}

	@Test
	void resolveFromLocalContext() {
		BiConsumer<String, Integer> tester = (key, index) -> {
			GraphQLContext context = GraphQLContext.newContext().of(key, this.book).build();
			Object actual = resolveValue(context, null, index);
			assertThat(actual).isSameAs(this.book);
		};
		tester.accept("book", 0);
		tester.accept("customKey", 1);
	}

	@Test
	@SuppressWarnings({"unchecked", "ConstantConditions"})
	void resolveMissing() {
		GraphQLContext context = GraphQLContext.newContext().build();

		// Required
		assertThatIllegalStateException()
				.isThrownBy(() -> resolveValue(context, context, 0))
				.withMessage("Missing required context value for method 'handle' parameter 0");

		// Not required
		assertThat(resolveValue(context, context, 2)).isNull();

		// Optional
		Optional<Book> actual = (Optional<Book>) resolveValue(context, context, 3);
		assertThat(actual.isPresent()).isFalse();
	}

	@Test
	@SuppressWarnings({"unchecked", "ConstantConditions", "OptionalGetWithoutIsPresent"})
	void resolveOptional() {
		GraphQLContext context = GraphQLContext.newContext().of("optionalBook", this.book).build();
		Optional<Book> actual = (Optional<Book>) resolveValue(context, context, 3);

		assertThat(actual.get()).isSameAs(this.book);
	}

	@Nullable
	private Object resolveValue(
			@Nullable GraphQLContext localContext, @Nullable GraphQLContext graphQLContext, int index) {

		DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
				.localContext(localContext)
				.graphQLContext(graphQLContext)
				.build();

		return this.resolver.resolveArgument(methodParam(index), environment);
	}

	private MethodParameter methodParam(int index) {
		MethodParameter methodParameter = new SynthesizingMethodParameter(method, index);
		methodParameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
		return methodParameter;
	}


	@SuppressWarnings({"unused", "rawtypes", "OptionalUsedAsFieldOrParameterType"})
	public void handle(
			@ContextValue Book book,
			@ContextValue("customKey") Book customKeyBook,
			@ContextValue(required = false) Book notRequiredBook,
			@ContextValue Optional<Book> optionalBook,
			Book otherBook) {
	}

}

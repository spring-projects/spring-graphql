/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import java.lang.reflect.Method;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.graphql.Book;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.LocalContextValue;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ContextValueMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
class LocalContextValueMethodArgumentResolverTests {

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

	private @Nullable Object resolveValue(@Nullable GraphQLContext localContext, int index) {

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

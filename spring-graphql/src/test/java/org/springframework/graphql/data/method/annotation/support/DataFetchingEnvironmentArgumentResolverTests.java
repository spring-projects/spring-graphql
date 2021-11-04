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
import java.util.Locale;
import java.util.Optional;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.DataFetchingFieldSelectionSet;
import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DataFetchingEnvironmentMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
public class DataFetchingEnvironmentArgumentResolverTests {

	private static final Method handleMethod = ClassUtils.getMethod(
			DataFetchingEnvironmentArgumentResolverTests.class, "handle", (Class<?>[]) null);


	private final DataFetchingEnvironmentMethodArgumentResolver resolver =
			new DataFetchingEnvironmentMethodArgumentResolver();

	@Test
	void supportsParameter() {
		assertThat(this.resolver.supportsParameter(parameter(0))).isTrue();
		assertThat(this.resolver.supportsParameter(parameter(1))).isTrue();
		assertThat(this.resolver.supportsParameter(parameter(2))).isTrue();
		assertThat(this.resolver.supportsParameter(parameter(3))).isTrue();

		assertThat(this.resolver.supportsParameter(parameter(4))).isFalse();
	}

	@Test
	void resolveGraphQlContext() {
		GraphQLContext context = GraphQLContext.newContext().build();
		DataFetchingEnvironment environment = environment().graphQLContext(context).build();
		Object actual = this.resolver.resolveArgument(parameter(0), environment);

		assertThat(actual).isSameAs(context);
	}

	@Test
	void resolveSelectionSet() {
		DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
		DataFetchingEnvironment environment = environment().selectionSet(selectionSet).build();
		Object actual = this.resolver.resolveArgument(parameter(1), environment);

		assertThat(actual).isSameAs(selectionSet);
	}

	@Test
	void resolveLocale() {
		Locale locale = Locale.ITALIAN;
		DataFetchingEnvironment environment = environment().locale(locale).build();
		Object actual = this.resolver.resolveArgument(parameter(2), environment);

		assertThat(actual).isSameAs(locale);
	}

	@SuppressWarnings("unchecked")
	@Test
	void resolveOptionalLocale() {
		Locale locale = Locale.ITALIAN;
		DataFetchingEnvironment environment = environment().locale(locale).build();
		Optional<Locale> actual = (Optional<Locale>) this.resolver.resolveArgument(parameter(3), environment);

		assertThat(actual).isNotNull();
		assertThat(actual.isPresent()).isTrue();
		assertThat(actual.get()).isSameAs(locale);
	}

	private static DataFetchingEnvironmentImpl.Builder environment() {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment();
	}

	private MethodParameter parameter(int index) {
		return new MethodParameter(handleMethod, index);
	}


	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
	public void handle(
			GraphQLContext graphQLContext,
			DataFetchingFieldSelectionSet selectionSet,
			Locale locale,
			Optional<Locale> optionalLocale,
			String s) {
	}

}

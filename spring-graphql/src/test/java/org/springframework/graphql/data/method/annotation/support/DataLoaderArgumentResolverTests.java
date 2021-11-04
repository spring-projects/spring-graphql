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

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.Author;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataLoaderMethodArgumentResolver}.
 * @author Rossen Stoyanchev
 */
public class DataLoaderArgumentResolverTests {

	private static final Method method = ClassUtils.getMethod(
			DataLoaderArgumentResolverTests.class, "handle", (Class<?>[]) null);

	private final DataLoaderMethodArgumentResolver resolver = new DataLoaderMethodArgumentResolver();

	private final BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();


	@Test
	void supportsParameter() {
		assertThat(this.resolver.supportsParameter(initParameter(0))).isTrue();
		assertThat(this.resolver.supportsParameter(initParameter(3))).isFalse();
	}

	@Test
	void resolveArgument() {
		this.registry.forTypePair(Long.class, Author.class).registerBatchLoader((ids, env) -> Flux.empty());

		Object argument = this.resolver.resolveArgument(initParameter(0), environment());
		assertThat(argument).isNotNull();
	}

	@Test
	void resolveArgumentViaParameterName() {
		this.registry.forName("namedDataLoader").registerBatchLoader((ids, env) -> Flux.empty());

		MethodParameter parameter = initParameter(1);
		parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());

		Object argument = this.resolver.resolveArgument(parameter, environment());
		assertThat(argument).isNotNull();
	}

	@Test
	void resolveArgumentFailureWithoutGenericType() {
		this.registry.forTypePair(Long.class, Author.class).registerBatchLoader((ids, env) -> Flux.empty());

		assertThatThrownBy(() -> this.resolver.resolveArgument(initParameter(2), environment()))
				.hasMessageContaining("declaring the DataLoader argument with generic types should help");
	}

	@Test
	void resolveArgumentFailureWithoutParameterName() {
		this.registry.forName("namedDataLoader").registerBatchLoader((ids, env) -> Flux.empty());

		MethodParameter parameter = initParameter(1);
		// Skip ParameterNameDiscovery

		assertThatThrownBy(() -> this.resolver.resolveArgument(parameter, environment()))
				.hasMessageContaining("compiling with \"-parameters\" should help");
	}

	@Test
	void resolveArgumentFailureNoMatch() {
		this.registry.forName("bookDataLoader").registerBatchLoader((ids, env) -> Flux.empty());

		MethodParameter parameter = initParameter(0);
		parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());

		assertThatThrownBy(() -> this.resolver.resolveArgument(parameter, environment()))
				.hasMessageContaining(
						"Neither the name of the declared value type 'class org.springframework.graphql.Author' " +
								"nor the method parameter name 'authorDataLoader' match to any DataLoader. " +
								"The DataLoaderRegistry contains: [bookDataLoader]");
	}

	private DataFetchingEnvironment environment() {
		DataLoaderRegistry dataLoaderRegistry = DataLoaderRegistry.newRegistry().build();
		this.registry.registerDataLoaders(dataLoaderRegistry, GraphQLContext.newContext().build());
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().dataLoaderRegistry(dataLoaderRegistry).build();
	}

	private MethodParameter initParameter(int index) {
		return new MethodParameter(method, index);
	}


	@SuppressWarnings({"unused", "rawtypes"})
	public void handle(
			DataLoader<Long, Author> authorDataLoader,
			DataLoader<Long, Author> namedDataLoader,
			DataLoader rawDataLoader,
			DataFetchingEnvironment environment) {
	}

}

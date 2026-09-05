/*
 * Copyright 2002-present the original author or authors.
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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import graphql.execution.DataFetcherResult;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.ApplicationContext;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.graphql.execution.ReactiveAdapterRegistryHelper;
import org.springframework.util.Assert;

/**
 * Applies an interface projection while preserving controller return value wrappers.
 *
 * @author Goutam Adwant
 */
final class ReturnValueProjector {

	private final Class<?> projectionType;

	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();


	ReturnValueProjector(Class<?> projectionType, ApplicationContext context) {
		Assert.isTrue(projectionType.isInterface(), "@ProjectAs requires an interface projection type");
		Assert.isTrue(!Iterable.class.isAssignableFrom(projectionType) && !Map.class.isAssignableFrom(projectionType),
				"@ProjectAs requires a projection interface, not a container type");
		this.projectionType = projectionType;
		this.projectionFactory.setBeanFactory(context);
		ClassLoader classLoader = context.getClassLoader();
		if (classLoader != null) {
			this.projectionFactory.setBeanClassLoader(classLoader);
		}
	}


	@Nullable Object project(@Nullable Object result) {
		result = ReactiveAdapterRegistryHelper.toMonoOrFluxIfReactive(result);
		if (result instanceof Mono<?> mono) {
			return mono.map((value) -> Objects.requireNonNull(project(value)));
		}
		if (result instanceof Flux<?> flux) {
			return flux.map((value) -> Objects.requireNonNull(project(value)));
		}
		if (result instanceof DataFetcherResult<?> dataFetcherResult) {
			return dataFetcherResult.map(this::project);
		}
		if (result instanceof Optional<?> optional) {
			return optional.map(this::project);
		}
		if (result instanceof Iterable<?> iterable) {
			List<@Nullable Object> values = new ArrayList<>();
			iterable.forEach((value) -> values.add(project(value)));
			return values;
		}
		if (result != null && result.getClass().isArray()) {
			int length = Array.getLength(result);
			List<@Nullable Object> values = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				values.add(project(Array.get(result, i)));
			}
			return values;
		}
		return (result != null) ? this.projectionFactory.createProjection(this.projectionType, result) : null;
	}

	ResolvableType getReturnType(ResolvableType returnType) {
		if (returnType.isArray()) {
			return ResolvableType.forArrayComponent(getReturnType(returnType.getComponentType()));
		}
		Class<?> type = returnType.resolve(Object.class);
		for (Class<?> wrapper : List.of(Iterable.class, Optional.class, Callable.class, DataFetcherResult.class)) {
			if (wrapper.isAssignableFrom(type)) {
				return ResolvableType.forClassWithGenerics(wrapper,
						getReturnType(returnType.as(wrapper).getGeneric(0)));
			}
		}
		ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(type);
		if (adapter != null) {
			return ResolvableType.forClassWithGenerics(adapter.isMultiValue() ? Flux.class : Mono.class,
					getReturnType(returnType.as(adapter.getReactiveType()).getGeneric(0)));
		}
		return ResolvableType.forClass(this.projectionType);
	}

}

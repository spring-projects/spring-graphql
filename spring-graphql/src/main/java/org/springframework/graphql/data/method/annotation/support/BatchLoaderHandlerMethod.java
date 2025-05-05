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

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import graphql.GraphQLContext;
import org.dataloader.BatchLoaderEnvironment;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.CollectionFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.InvocableHandlerMethodSupport;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.execution.ReactiveAdapterRegistryHelper;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * An extension of {@link HandlerMethod} for annotated handler methods adapted to
 * {@link org.dataloader.BatchLoaderWithContext} or
 * {@link org.dataloader.MappedBatchLoaderWithContext} with the list of keys and
 * {@link BatchLoaderEnvironment} as their input.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class BatchLoaderHandlerMethod extends InvocableHandlerMethodSupport {

	private static final boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext",
			AnnotatedControllerConfigurer.class.getClassLoader());


	private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	/**
	 * Create an instance.
	 * @param handlerMethod the controller method
	 * @param executor an {@link Executor} to use for {@link Callable} return values
	 * @deprecated in favor of alternative constructor
	 */
	@Deprecated(since = "1.3.0", forRemoval = true)
	public BatchLoaderHandlerMethod(HandlerMethod handlerMethod, @Nullable Executor executor) {
		this(handlerMethod, executor, false);
	}

	/**
	 * Create an instance.
	 * @param handlerMethod the controller method
	 * @param executor an {@link Executor} to use for {@link Callable} return values
	 * @param invokeAsync whether to invoke the method through the Executor
	 * @since 1.3.0
	 */
	public BatchLoaderHandlerMethod(HandlerMethod handlerMethod, @Nullable Executor executor, boolean invokeAsync) {
		super(handlerMethod, executor, invokeAsync);
	}


	/**
	 * Invoke the underlying batch loader method with a collection of keys to
	 * return a Map of key-value pairs.
	 * @param keys the keys for which to load values
	 * @param environment the environment available to batch loaders
	 * @param <K> the type of keys in the map
	 * @param <V> the type of values in the map
	 * @return a {@code Mono} with map of key-value pairs.
	 */
	public @Nullable <K, V> Mono<Map<K, V>> invokeForMap(Collection<K> keys, BatchLoaderEnvironment environment) {
		@Nullable Object[] args = getMethodArgumentValues(keys, environment);
		GraphQLContext context = environment.getContext();
		Assert.notNull(context, "No GraphQLContext available");
		if (doesNotHaveAsyncArgs(args)) {
			Object result = doInvoke(context, args);
			return ReactiveAdapterRegistryHelper.toMono(result);
		}
		return toArgsMono(args).flatMap((argValues) -> {
			Object result = doInvoke(context, argValues);
			return ReactiveAdapterRegistryHelper.toMono(result);
		});
	}

	/**
	 * Invoke the underlying batch loader method with a collection of input keys
	 * to return a collection of matching values.
	 * @param keys the keys for which to load values
	 * @param environment the environment available to batch loaders
	 * @param <V> the type of values returned
	 * @return a {@code Flux} of values.
	 */
	public <V> Flux<V> invokeForIterable(Collection<?> keys, BatchLoaderEnvironment environment) {
		@Nullable Object[] args = getMethodArgumentValues(keys, environment);
		GraphQLContext context = environment.getContext();
		Assert.notNull(context, "No GraphQLContext available");
		if (doesNotHaveAsyncArgs(args)) {
			Object result = doInvoke(context, args);
			return ReactiveAdapterRegistryHelper.toFluxFromCollection(result);
		}
		return toArgsMono(args).flatMapMany((resolvedArgs) -> {
			Object result = doInvoke(context, resolvedArgs);
			return ReactiveAdapterRegistryHelper.toFluxFromCollection(result);
		});
	}

	private <K> @Nullable Object[] getMethodArgumentValues(Collection<K> keys, BatchLoaderEnvironment environment) {
		@Nullable Object[] args = new Object[getMethodParameters().length];
		for (int i = 0; i < getMethodParameters().length; i++) {
			args[i] = resolveArgument(getMethodParameters()[i], keys, environment);
		}
		return args;
	}

	private @Nullable <K> Object resolveArgument(
			MethodParameter parameter, Collection<K> keys, BatchLoaderEnvironment environment) {

		parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);

		Class<?> parameterType = parameter.getParameterType();

		if (Collection.class.isAssignableFrom(parameterType)) {
			if (parameterType.isInstance(keys)) {
				return keys;
			}
			Class<?> elementType = parameter.nested().getNestedParameterType();
			Collection<K> collection = CollectionFactory.createCollection(parameterType, elementType, keys.size());
			collection.addAll(keys);
			return collection;
		}
		else if (parameter.hasParameterAnnotation(ContextValue.class)) {
			return resolveContextValueArgument(parameter, environment);
		}
		else if (parameterType.equals(GraphQLContext.class)) {
			return environment.getContext();
		}
		else if (parameterType.isInstance(environment)) {
			return environment;
		}
		else if ("kotlin.coroutines.Continuation".equals(parameterType.getName())) {
			return null;
		}
		else if (springSecurityPresent && Principal.class.isAssignableFrom(parameter.getParameterType())) {
			return PrincipalMethodArgumentResolver.resolveAuthentication(parameter);
		}
		else {
			throw new IllegalStateException(formatArgumentError(parameter, "Unexpected argument type."));
		}
	}

	private @Nullable Object resolveContextValueArgument(MethodParameter parameter, BatchLoaderEnvironment environment) {

		ContextValue annotation = parameter.getParameterAnnotation(ContextValue.class);
		Assert.state(annotation != null, "Expected @ContextValue annotation");
		String name = ContextValueMethodArgumentResolver.getContextValueName(parameter, annotation.name(), annotation);

		return ContextValueMethodArgumentResolver.resolveContextValue(
				name, annotation.required(), parameter, environment.getContext());
	}

	private boolean doesNotHaveAsyncArgs(@Nullable Object[] args) {
		return Arrays.stream(args).noneMatch((arg) -> arg instanceof Mono);
	}

}

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

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import graphql.GraphQLContext;
import org.dataloader.BatchLoaderEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.InvocableHandlerMethodSupport;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.lang.Nullable;
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

	private final static boolean springSecurityPresent = ClassUtils.isPresent(
			"org.springframework.security.core.context.SecurityContext",
			AnnotatedControllerConfigurer.class.getClassLoader());


	public BatchLoaderHandlerMethod(HandlerMethod handlerMethod, @Nullable Executor executor) {
		super(handlerMethod, executor);
	}


	/**
	 * Invoke the underlying batch loader method with a collection of keys to
	 * return a Map of key-value pairs.
	 *
	 * @param keys the keys for which to load values
	 * @param environment the environment available to batch loaders
	 * @param <K> the type of keys in the map
	 * @param <V> the type of values in the map
	 * @return a {@code Mono} with map of key-value pairs.
	 */
	@Nullable
	public <K, V> Mono<Map<K, V>> invokeForMap(Collection<K> keys, BatchLoaderEnvironment environment) {
		Object[] args = getMethodArgumentValues(keys, environment);
		if (doesNotHaveAsyncArgs(args)) {
			Object result = doInvoke(environment.getContext(), args);
			return toMonoMap(result);
		}
		return toArgsMono(args).flatMap(argValues -> {
			Object result = doInvoke(environment.getContext(), argValues);
			return toMonoMap(result);
		});
	}

	/**
	 * Invoke the underlying batch loader method with a collection of input keys
	 * to return a collection of matching values.
	 *
	 * @param keys the keys for which to load values
	 * @param environment the environment available to batch loaders
	 * @param <V> the type of values returned
	 * @return a {@code Flux} of values.
	 */
	public <V> Flux<V> invokeForIterable(Collection<?> keys, BatchLoaderEnvironment environment) {
		Object[] args = getMethodArgumentValues(keys, environment);
		if (doesNotHaveAsyncArgs(args)) {
			Object result = doInvoke(environment.getContext(), args);
			return toFlux(result);
		}
		return toArgsMono(args).flatMapMany(resolvedArgs -> {
			Object result = doInvoke(environment.getContext(), resolvedArgs);
			return toFlux(result);
		});
	}

	private <K> Object[] getMethodArgumentValues(Collection<K> keys, BatchLoaderEnvironment environment) {
		Object[] args = new Object[getMethodParameters().length];
		for (int i = 0; i < getMethodParameters().length; i++) {
			args[i] = resolveArgument(getMethodParameters()[i], keys, environment);
		}
		return args;
	}

	@Nullable
	private  <K> Object resolveArgument(
			MethodParameter parameter, Collection<K> keys, BatchLoaderEnvironment environment) {

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
			return PrincipalMethodArgumentResolver.doResolve();
		}
		else {
			throw new IllegalStateException(formatArgumentError(parameter, "Unexpected argument type."));
		}
	}

	@Nullable
	private Object resolveContextValueArgument(MethodParameter parameter, BatchLoaderEnvironment environment) {

		ContextValue annotation = parameter.getParameterAnnotation(ContextValue.class);
		Assert.state(annotation != null, "Expected @ContextValue annotation");
		String name = ContextValueMethodArgumentResolver.getContextValueName(parameter, annotation.name(), annotation);

		return ContextValueMethodArgumentResolver.resolveContextValue(
				name, annotation.required(), parameter, environment.getContext());
	}

	private boolean doesNotHaveAsyncArgs(Object[] args) {
		return Arrays.stream(args).noneMatch(arg -> arg instanceof Mono);
	}

	@SuppressWarnings("unchecked")
	private static <K, V> Mono<Map<K, V>> toMonoMap(@Nullable Object result) {
		if (result instanceof Map) {
			return Mono.just((Map<K, V>) result);
		}
		else if (result instanceof Mono) {
			return (Mono<Map<K, V>>) result;
		}
		else if (result instanceof CompletableFuture) {
			return Mono.fromFuture((CompletableFuture<? extends Map<K,V>>) result);
		}
		return Mono.error(new IllegalStateException("Unexpected return value: " + result));
	}

	@SuppressWarnings("unchecked")
	private static <V> Flux<V> toFlux(@Nullable Object result) {
		if (result instanceof Collection) {
			return Flux.fromIterable((Collection<V>) result);
		}
		else if (result instanceof Flux) {
			return (Flux<V>) result;
		}
		else if (result instanceof CompletableFuture) {
			return Mono.fromFuture((CompletableFuture<? extends Collection<V>>) result)
					.flatMapIterable(Function.identity());
		}
		return Flux.error(new IllegalStateException("Unexpected return value: " + result));
	}

}

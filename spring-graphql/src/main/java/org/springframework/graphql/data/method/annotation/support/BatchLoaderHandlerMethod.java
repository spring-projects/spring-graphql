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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.dataloader.BatchLoaderEnvironment;
import org.springframework.graphql.data.method.BatchHandlerMethodArgumentResolverComposite;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.InvocableHandlerMethodSupport;
import org.springframework.lang.Nullable;

/**
 * An extension of {@link HandlerMethod} for annotated handler methods adapted to
 * {@link org.dataloader.BatchLoaderWithContext} or
 * {@link org.dataloader.MappedBatchLoaderWithContext} with the list of keys and
 * {@link BatchLoaderEnvironment} as their input.
 *
 * @author Rossen Stoyanchev
 * @author Genkui Du
 * @since 1.0.0
 */
public class BatchLoaderHandlerMethod extends InvocableHandlerMethodSupport {

	private static final Object[] EMPTY_ARGS = new Object[0];

	private final BatchHandlerMethodArgumentResolverComposite resolvers;

	public BatchLoaderHandlerMethod(HandlerMethod handlerMethod, BatchHandlerMethodArgumentResolverComposite resolvers) {
		super(handlerMethod);
		Assert.isTrue(!resolvers.getArgumentResolvers().isEmpty(), "No argument resolvers");
		this.resolvers = resolvers;
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
		Object[] args;
		try {
			args = getMethodArgumentValues(keys, environment);
		}
		catch (Throwable ex) {
			return Mono.error(ex);
		}
		if (doesNotHaveAsyncArgs(args)) {
			Object result = doInvoke(args);
			return toMonoMap(result);
		}
		return toArgsMono(args).flatMap(argValues -> {
			Object result = doInvoke(argValues);
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
		Object[] args;
		try {
			args = getMethodArgumentValues(keys, environment);
		}
		catch (Throwable ex) {
			return Flux.error(ex);
		}
		if (doesNotHaveAsyncArgs(args)) {
			Object result = doInvoke(args);
			return toFlux(result);
		}
		return toArgsMono(args).flatMapMany(resolvedArgs -> {
			Object result = doInvoke(resolvedArgs);
			return toFlux(result);
		});
	}

	@SuppressWarnings("unchecked")
	private <K> Object[] getMethodArgumentValues(Collection<K> keys, BatchLoaderEnvironment environment) throws Exception {

		MethodParameter[] parameters = getMethodParameters();
		if (ObjectUtils.isEmpty(parameters)) {
			return EMPTY_ARGS;
		}

		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			if(!this.resolvers.supportsParameter(parameter)){
				throw new IllegalStateException(formatArgumentError(parameter, "Unexpected argument type."));
			}
			try {
				args[i] = this.resolvers.resolveArgument(parameter, keys, (Map) environment.getKeyContexts(), environment);
			}
			catch (Exception ex) {
				// Leave stack trace for later, exception may actually be resolved and handled...
				if (logger.isDebugEnabled()) {
					String exMsg = ex.getMessage();
					if (exMsg != null && !exMsg.contains(parameter.getExecutable().toGenericString())) {
						logger.debug(formatArgumentError(parameter, exMsg));
					}
				}
				throw ex;
			}

		}
		return args;
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
		return Flux.error(new IllegalStateException("Unexpected return value: " + result));
	}

}
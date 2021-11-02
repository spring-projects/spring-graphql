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

import java.util.Collection;
import java.util.Map;

import org.dataloader.BatchLoaderEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.InvocableHandlerMethodSupport;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

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


	public BatchLoaderHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}


	/**
	 * Invoke the underlying batch loading method, resolving its arguments from
	 * the given keys  and the {@link BatchLoaderEnvironment}.
	 *
	 * @param keys the batch loading keys
	 * @param environment the environment available to batch loaders
	 * @return a {@code Flux} of values or a {@code Mono} with map of key-value pairs.
	 */
	@Nullable
	public <K> Object invoke(Collection<K> keys, BatchLoaderEnvironment environment) {

		MethodParameter[] parameters = getMethodParameters();
		Assert.notEmpty(parameters, "Batch loading methods should have at least " +
				"one argument with the List of parent objects: " + getBridgedMethod().toGenericString());

		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			args[i] = resolveArgument(parameters[i], keys, environment);
		}

		Object result;
		try {
			result = doInvoke(args);
		}
		catch (Exception ex) {
			throw new IllegalStateException("...", ex);
		}

		if (result != null) {
			if (result instanceof Collection) {
				return Flux.fromIterable((Collection<?>) result);
			}
			else if (result instanceof Map) {
				return Mono.just(result);
			}
		}

		return result;
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
		else if (parameterType.isInstance(environment)) {
			return environment;
		}
		else if ("kotlin.coroutines.Continuation".equals(parameterType.getName())) {
			return null;
		}
		else {
			throw new IllegalStateException(formatArgumentError(parameter, "Unexpected argument type."));
		}
	}

}

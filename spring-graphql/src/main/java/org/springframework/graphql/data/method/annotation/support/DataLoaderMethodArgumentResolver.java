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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.util.Assert;

/**
 * Resolver for a {@link DataLoader} obtained via
 * {@link DataFetchingEnvironment#getDataLoader(String)}.
 *
 * <p>The {@code DataLoader} key is based on one of the following:
 * <ol>
 * <li>The full name of the value type from the DataLoader generic types.</li>
 * <li>The method parameter name.</li>
 * </ol>
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DataLoaderMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(DataLoader.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {
		DataLoader<Object, Object> dataLoader = null;

		Class<?> valueType = getValueType(parameter);
		if (valueType != null) {
			dataLoader = environment.getDataLoader(valueType.getName());
		}

		String parameterName = null;
		if (dataLoader == null) {
			parameterName = parameter.getParameterName();
			if (parameterName != null) {
				dataLoader = environment.getDataLoader(parameterName);
			}
		}

		if (dataLoader == null) {
			String message = getErrorMessage(parameter, environment, valueType, parameterName);
			throw new IllegalArgumentException(message);
		}

		return dataLoader;
	}

	private @Nullable Class<?> getValueType(MethodParameter param) {
		Assert.isAssignable(DataLoader.class, param.getParameterType());
		Type genericType = param.getGenericParameterType();
		if (genericType instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) genericType;
			if (parameterizedType.getActualTypeArguments().length == 2) {
				Type valueType = parameterizedType.getActualTypeArguments()[1];
				return (valueType instanceof Class) ?
						(Class<?>) valueType : ResolvableType.forType(valueType).resolve();
			}
		}
		return null;
	}

	private String getErrorMessage(
			MethodParameter parameter, DataFetchingEnvironment environment,
			@Nullable Class<?> valueType, @Nullable String parameterName) {

		StringBuilder builder = new StringBuilder("Cannot resolve DataLoader for parameter");

		if (parameterName != null) {
			builder.append(" '").append(parameterName).append("'");
		}
		else {
			builder.append("[").append(parameter.getParameterIndex()).append("]");
		}
		if (parameter.getMethod() != null) {
			builder.append(" in method ").append(parameter.getMethod().toGenericString());
		}
		builder.append(". ");

		if (valueType == null) {
			builder.append("If the batch loader was registered without a name, " +
					"then declaring the DataLoader argument with generic types should help " +
					"to look up the DataLoader based on the value type name.");
		}
		else if (parameterName == null) {
			builder.append("If the batch loader was registered with a name, " +
					"then compiling with \"-parameters\" should help " +
					"to look up the DataLoader based on the parameter name.");
		}
		else {
			builder.append("Neither the name of the declared value type '").append(valueType)
					.append("' nor the method parameter name '").append(parameterName)
					.append("' match to any DataLoader. The DataLoaderRegistry contains: ")
					.append(environment.getDataLoaderRegistry().getKeys());
		}
		return builder.toString();
	}

}

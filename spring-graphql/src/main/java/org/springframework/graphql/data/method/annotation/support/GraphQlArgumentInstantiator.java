/*
 * Copyright 2020-2021 the original author or authors.
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

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.MethodParameter;
import org.springframework.validation.DataBinder;

/**
 * Instantiate a target type and bind data from
 * {@link graphql.schema.DataFetchingEnvironment} arguments.
 *
 * @author Brian Clozel
 */
class GraphQlArgumentInstantiator {

	/**
	 * Instantiate the given target type and bind data from
	 * {@link graphql.schema.DataFetchingEnvironment} arguments.
	 * <p>This is considering the default constructor or a primary constructor
	 * if available.
	 *
	 * @param targetType the type of the argument to instantiate
	 * @param arguments the data fetching environment arguments
	 * @param <T> the type of the input argument
	 * @return the instantiated and populated input argument.
	 * @throws IllegalStateException if there is no suitable constructor.
	 */
	@SuppressWarnings("unchecked")
	public <T> T instantiate(Class<T> targetType, Map<String, Object> arguments) {
		Object target;
		Constructor<?> ctor = BeanUtils.getResolvableConstructor(targetType);
		MutablePropertyValues propertyValues = extractPropertyValues(arguments);

		if (ctor.getParameterCount() == 0) {
			target = BeanUtils.instantiateClass(ctor);
			DataBinder dataBinder = new DataBinder(target);
			dataBinder.bind(propertyValues);
		}
		else {
			// Data class constructor
			DataBinder binder = new DataBinder(null);
			String[] paramNames = BeanUtils.getParameterNames(ctor);
			Class<?>[] paramTypes = ctor.getParameterTypes();
			Object[] args = new Object[paramTypes.length];
			for (int i = 0; i < paramNames.length; i++) {
				String paramName = paramNames[i];
				Object value = propertyValues.get(paramName);
				value = (value instanceof List ? ((List<?>) value).toArray() : value);
				MethodParameter methodParam = new MethodParameter(ctor, i);
				if (value == null && methodParam.isOptional()) {
					args[i] = (methodParam.getParameterType() == Optional.class ? Optional.empty() : null);
				}
				else {
					args[i] = binder.convertIfNecessary(value, paramTypes[i], methodParam);
				}
			}
			target = BeanUtils.instantiateClass(ctor, args);
		}
		return (T) target;
	}

	/**
	 * Perform a Depth First Search in the given JSON map to collect attribute values
	 * as {@link MutablePropertyValues} using the full property path as key.
	 */
	private MutablePropertyValues extractPropertyValues(Map<String, Object> arguments) {
		MutablePropertyValues mpvs = new MutablePropertyValues();
		Stack<String> path = new Stack<>();
		visitArgumentMap(arguments, mpvs, path);
		return mpvs;
	}

	@SuppressWarnings("unchecked")
	private void visitArgumentMap(Map<String, Object> arguments, MutablePropertyValues mpvs, Stack<String> path) {
		for (String key : arguments.keySet()) {
			Object value = arguments.get(key);
			if (value instanceof List) {
				List<Object> items = (List<Object>) value;
				Map<String, Object> subValues = new HashMap<>(items.size());
				for (int i = 0; i < items.size(); i++) {
					subValues.put(key + "[" + i + "]", items.get(i));
				}
				visitArgumentMap(subValues, mpvs, path);
			}
			else if (value instanceof Map) {
				path.push(key);
				path.push(".");
				visitArgumentMap((Map<String, Object>) value, mpvs, path);
				path.pop();
				path.pop();
			}
			else {
				path.push(key);
				String propertyName = pathToPropertyName(path);
				mpvs.add(propertyName, value);
				path.pop();
			}
		}
	}

	private String pathToPropertyName(Stack<String> path) {
		StringBuilder sb = new StringBuilder();
		for (String s : path) {
			sb.append(s);
		}
		return sb.toString();
	}
}

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

package org.springframework.graphql.data;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.DataBinder;

/**
 * Instantiate a target type and bind data from
 * {@link graphql.schema.DataFetchingEnvironment} arguments.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlArgumentInitializer {

	private final SimpleTypeConverter typeConverter;


	public GraphQlArgumentInitializer(@Nullable ConversionService conversionService) {
		this.typeConverter = new SimpleTypeConverter();
		this.typeConverter.setConversionService(conversionService);
	}


	/**
	 * Initialize an Object of the given {@code targetType}, either from a named
	 * {@link DataFetchingEnvironment#getArgument(String) argument value}, or from all
	 * {@link DataFetchingEnvironment#getArguments() values} as the source.
	 * @param environment the environment with the argument values
	 * @param name optionally, the name of an argument to initialize from,
	 * or if {@code null}, the full map of arguments is used.
	 * @param targetType the type of Object to initialize
	 * @return the initialized Object, or {@code null}
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public Object initializeArgument(
			DataFetchingEnvironment environment, @Nullable String name, ResolvableType targetType) {

		Object sourceValue = (name != null ? environment.getArgument(name) : environment.getArguments());

		if (sourceValue == null) {
			return wrapAsOptionalIfNecessary(null, targetType);
		}

		Class<?> targetClass = targetType.resolve();
		Assert.notNull(targetClass, "Could not determine target type from " + targetType);

		// From Collection

		if (CollectionFactory.isApproximableCollectionType(sourceValue.getClass())) {
			Assert.isAssignable(Collection.class, targetClass,
					"Argument '" + name + "' is a Collection while method parameter is " + targetClass.getName());
			Class<?> elementType = targetType.asCollection().getGeneric(0).resolve();
			Assert.notNull(elementType, "Could not determine element type for " + targetType);
			return initializeFromCollection((Collection<Object>) sourceValue, elementType);
		}

		if (targetClass == Optional.class) {
			targetClass = targetType.getNested(2).resolve();
			Assert.notNull(targetClass, "Could not determine Optional<T> type from " + targetType);
		}

		// From Map

		if (sourceValue instanceof Map) {
			Object target = initializeFromMap((Map<String, Object>) sourceValue, targetClass);
			return wrapAsOptionalIfNecessary(target, targetType);
		}

		// From Scalar

		if (targetClass.isInstance(sourceValue)) {
			return wrapAsOptionalIfNecessary(sourceValue, targetType);
		}

		Object target = this.typeConverter.convertIfNecessary(sourceValue, targetClass);
		if (target == null) {
			throw new IllegalStateException("Cannot convert argument value " +
					"type [" + sourceValue.getClass().getName() + "] to method parameter " +
					"type [" + targetClass.getName() + "].");
		}

		return wrapAsOptionalIfNecessary(target, targetType);
	}

	@Nullable
	private Object wrapAsOptionalIfNecessary(@Nullable Object value, ResolvableType type) {
		return (type.resolve(Object.class).equals(Optional.class) ? Optional.ofNullable(value) : value);
	}

	/**
	 * Instantiate a collection of {@code elementType} using the given {@code values}.
	 * <p>This will instantiate a new Collection of the closest type possible
	 * from the one provided as an argument.
	 *
	 * @param <T> the type of Collection elements
	 * @param values the collection of values to bind and instantiate
	 * @param elementClass the type of elements in the given Collection
	 * @return the instantiated and populated Collection.
	 * @throws IllegalStateException if there is no suitable constructor.
	 */
	@SuppressWarnings("unchecked")
	private <T> Collection<T> initializeFromCollection(Collection<Object> values, Class<T> elementClass) {
		Collection<T> collection = CollectionFactory.createApproximateCollection(values, values.size());
		for (Object item : values) {
			if (elementClass.isAssignableFrom(item.getClass())) {
				collection.add((T) item);
			}
			else if (item instanceof Map) {
				collection.add((T) this.initializeFromMap((Map<String, Object>) item, elementClass));
			}
			else {
				collection.add(this.typeConverter.convertIfNecessary(item, elementClass));
			}
		}
		return collection;
	}

	/**
	 * Instantiate an Object of the given target type and bind
	 * {@link graphql.schema.DataFetchingEnvironment} argument values to it.
	 * This considers the default constructor or a primary constructor, if available.
	 * @throws IllegalStateException if there is no suitable constructor.
	 */
	@SuppressWarnings("unchecked")
	private Object initializeFromMap(Map<String, Object> arguments, Class<?> targetType) {
		Object target;
		Constructor<?> ctor = BeanUtils.getResolvableConstructor(targetType);

		if (ctor.getParameterCount() == 0) {
			MutablePropertyValues propertyValues = extractPropertyValues(arguments);
			target = BeanUtils.instantiateClass(ctor);
			DataBinder dataBinder = new DataBinder(target);
			dataBinder.bind(propertyValues);
			return target;
		}

		// Data class constructor

		String[] paramNames = BeanUtils.getParameterNames(ctor);
		Class<?>[] paramTypes = ctor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];
		for (int i = 0; i < paramNames.length; i++) {
			String paramName = paramNames[i];
			Object value = arguments.get(paramName);
			MethodParameter methodParameter = new MethodParameter(ctor, i);
			if (value == null && methodParameter.isOptional()) {
				args[i] = (methodParameter.getParameterType() == Optional.class ? Optional.empty() : null);
			}
			else if (value != null && CollectionFactory.isApproximableCollectionType(value.getClass())) {
				ResolvableType resolvableType = ResolvableType.forMethodParameter(methodParameter);
				Class<?> elementType = resolvableType.asCollection().getGeneric(0).resolve();
				Assert.notNull(elementType, "Cannot determine element type for " + resolvableType);
				args[i] = initializeFromCollection((Collection<Object>) value, elementType);
			}
			else if (value instanceof Map) {
				args[i] = this.initializeFromMap((Map<String, Object>) value, methodParameter.getParameterType());
			}
			else {
				args[i] = this.typeConverter.convertIfNecessary(value, paramTypes[i], methodParameter);
			}
		}

		return BeanUtils.instantiateClass(ctor, args);
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

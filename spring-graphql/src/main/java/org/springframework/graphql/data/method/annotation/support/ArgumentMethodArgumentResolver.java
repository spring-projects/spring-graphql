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

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ValueConstants;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder;

/**
 * Resolver for {@link Argument @Argument} annotated method parameters, obtained
 * via {@link DataFetchingEnvironment#getArgument(String)} and converted to the
 * declared type of the method parameter.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class ArgumentMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterAnnotation(Argument.class) != null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		Argument annotation = parameter.getParameterAnnotation(Argument.class);
		Assert.notNull(annotation, "No @Argument annotation");
		String name = annotation.name();
		if (!StringUtils.hasText(name)) {
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument of type [" + parameter.getNestedParameterType().getName() +
								"] not specified, and parameter name information not found in class file either.");
			}
		}

		Object rawValue = (ValueConstants.DEFAULT_NONE.equals(annotation.defaultValue()) ?
				environment.getArgument(name) :
				environment.getArgumentOrDefault(name, annotation.defaultValue()));

		TypeDescriptor parameterType = new TypeDescriptor(parameter);

		if (rawValue == null) {
			if (annotation.required()) {
				throw new MissingArgumentException(name, parameter);
			}
			if (parameterType.getType().equals(Optional.class)) {
				return Optional.empty();
			}
			return null;
		}

		if (CollectionFactory.isApproximableCollectionType(rawValue.getClass())) {
			Assert.isAssignable(Collection.class, parameterType.getType(),
					"Argument '" + name + "' is a Collection while the @Argument method parameter is " + parameterType.getType());
			Collection<Object> rawCollection = (Collection<Object>) rawValue;
			Collection<Object> values = CollectionFactory.createApproximateCollection(rawValue, rawCollection.size());
			Class<?> elementType = parameterType.getElementTypeDescriptor().getType();
			rawCollection.forEach(item -> values.add(convert(item, elementType)));
			return values;
		}

		MethodParameter nestedParameter = parameter.nestedIfOptional();
		Object value = convert(rawValue, nestedParameter.getNestedParameterType());
		return returnValue(value, parameterType.getType());
	}

	private Object returnValue(Object value, Class<?> parameterType) {
		return (parameterType.equals(Optional.class) ? Optional.of(value) : value);
	}

	@SuppressWarnings("unchecked")
	private Object convert(Object rawValue, Class<?> targetType) {
		Object target;
		if (rawValue instanceof Map) {
			Constructor<?> ctor = BeanUtils.getResolvableConstructor(targetType);
			target = BeanUtils.instantiateClass(ctor);
			DataBinder dataBinder = new DataBinder(target);
			Assert.isTrue(ctor.getParameterCount() == 0,
					() -> "Argument of type [" + targetType.getName() +
							"] cannot be instantiated because of missing default constructor.");
			MutablePropertyValues mpvs = extractPropertyValues((Map) rawValue);
			dataBinder.bind(mpvs);
		}
		else if (targetType.isAssignableFrom(rawValue.getClass())) {
			return returnValue(rawValue, targetType);
		}
		else {
			DataBinder converter = new DataBinder(null);
			target = converter.convertIfNecessary(rawValue, targetType);
			Assert.isTrue(target != null,
					() -> "Value of type [" + rawValue.getClass() + "] cannot be converted to argument of type [" +
							targetType.getName() + "].");
		}
		return target;
	}

	private MutablePropertyValues extractPropertyValues(Map<String, Object> arguments) {
		MutablePropertyValues mpvs = new MutablePropertyValues();
		Stack<String> path = new Stack<>();
		visitArgumentMap(arguments, mpvs, path);
		return mpvs;
	}

	@SuppressWarnings("unchecked")
	private void visitArgumentMap(Map<String, Object> arguments, MutablePropertyValues mpvs, Stack<String> path) {
		for (String key : arguments.keySet()) {
			path.push(key);
			Object value = arguments.get(key);
			if (value instanceof Map) {
				visitArgumentMap((Map<String, Object>) value, mpvs, path);
			}
			else {
				String propertyName = pathToPropertyName(path);
				mpvs.add(propertyName, value);
			}
			path.pop();
		}
	}

	private String pathToPropertyName(Stack<String> path) {
		StringBuilder sb = new StringBuilder();
		Iterator<String> it = path.iterator();
		while (it.hasNext()) {
			sb.append(it.next());
			if (it.hasNext()) {
				sb.append(".");
			}
		}
		return sb.toString();
	}

}

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
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.lang.Nullable;
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

	private final GraphQlArgumentInstantiator instantiator;

	private final ConversionService conversionService;

	public ArgumentMethodArgumentResolver(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
		this.instantiator = new GraphQlArgumentInstantiator(conversionService);
	}

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

		Object rawValue = environment.getArgument(name);
		TypeDescriptor parameterType = new TypeDescriptor(parameter);

		if (rawValue == null) {
			return returnValue(rawValue, parameterType.getType());
		}

		if (CollectionFactory.isApproximableCollectionType(rawValue.getClass())) {
			Assert.isAssignable(Collection.class, parameterType.getType(),
					"Argument '" + name + "' is a Collection while the @Argument method parameter is " + parameterType.getType());
			Class<?> elementType = parameterType.getElementTypeDescriptor().getType();
			return this.instantiator.instantiateCollection(elementType, (Collection<Object>) rawValue);
		}

		MethodParameter nestedParameter = parameter.nestedIfOptional();
		Object value = convert(rawValue, nestedParameter.getNestedParameterType());
		return returnValue(value, parameterType.getType());
	}

	private Object returnValue(Object value, Class<?> parameterType) {
		if (parameterType.equals(Optional.class)) {
			return Optional.ofNullable(value);
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	Object convert(Object rawValue, Class<?> targetType) {
		Object target;
		if (rawValue instanceof Map) {
			target = this.instantiator.instantiate((Map<String, Object>) rawValue, targetType);
		}
		else if (targetType.isAssignableFrom(rawValue.getClass())) {
			return returnValue(rawValue, targetType);
		}
		else {
			DataBinder converter = new DataBinder(null);
			converter.setConversionService(this.conversionService);
			target = converter.convertIfNecessary(rawValue, targetType);
			Assert.isTrue(target != null,
					() -> "Value of type [" + rawValue.getClass() + "] cannot be converted to argument of type [" +
							targetType.getName() + "].");
		}
		return target;
	}

}

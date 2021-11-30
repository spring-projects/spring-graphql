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
import org.springframework.graphql.data.GraphQlArgumentInitializer;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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

	private final GraphQlArgumentInitializer argumentInitializer;


	public ArgumentMethodArgumentResolver(@Nullable ConversionService conversionService) {
		this.argumentInitializer = new GraphQlArgumentInitializer(conversionService);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterAnnotation(Argument.class) != null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		String name = getArgumentName(parameter);
		Object rawValue = environment.getArgument(name);
		TypeDescriptor typeDescriptor = new TypeDescriptor(parameter);

		if (rawValue == null) {
			return wrapAsOptionalIfNecessary(null, typeDescriptor.getType());
		}

		// From Collection

		if (CollectionFactory.isApproximableCollectionType(rawValue.getClass())) {
			Assert.isAssignable(Collection.class, typeDescriptor.getType(),
					"Argument '" + name + "' is a Collection " +
							"while the @Argument method parameter is " + typeDescriptor.getType());
			Class<?> elementType = typeDescriptor.getElementTypeDescriptor().getType();
			return this.argumentInitializer.initializeFromCollection((Collection<Object>) rawValue, elementType);
		}

		Class<?> targetType = parameter.nestedIfOptional().getNestedParameterType();
		Object target;

		// From Map

		if (rawValue instanceof Map) {
			target = this.argumentInitializer.initializeFromMap((Map<String, Object>) rawValue, targetType);
			return wrapAsOptionalIfNecessary(target, typeDescriptor.getType());
		}

		// From Scalar

		if (targetType.isAssignableFrom(rawValue.getClass())) {
			return wrapAsOptionalIfNecessary(rawValue, targetType);
		}

		target = this.argumentInitializer.getTypeConverter().convertIfNecessary(rawValue, targetType);
		Assert.state(target != null, () ->
				"Cannot convert value type [" + rawValue.getClass() + "] " +
						"to argument type [" + targetType.getName() + "].");
		return wrapAsOptionalIfNecessary(target, typeDescriptor.getType());
	}

	static String getArgumentName(MethodParameter parameter) {
		Argument annotation = parameter.getParameterAnnotation(Argument.class);
		Assert.state(annotation != null, "Expected @Argument annotation");
		if (StringUtils.hasText(annotation.name())) {
			return annotation.name();
		}
		String parameterName = parameter.getParameterName();
		if (parameterName != null) {
			return parameterName;
		}
		throw new IllegalArgumentException(
				"Name for argument of type [" + parameter.getNestedParameterType().getName() +
						"] not specified, and parameter name information not found in class file either.");
	}

	@Nullable
	private Object wrapAsOptionalIfNecessary(@Nullable Object value, Class<?> type) {
		return (type.equals(Optional.class) ? Optional.ofNullable(value) : value);
	}

}

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

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Resolver for a method parameter that is annotated with
 * {@link Argument @Argument}. The specified raw argument value is obtained via
 * {@link DataFetchingEnvironment#getArgument(String)} and bound to a higher
 * level object, via {@link GraphQlArgumentBinder}, to match the target method
 * parameter type.
 *
 * <p>This resolver also supports wrapping the target object with
 * {@link ArgumentValue} if the application wants to differentiate between an
 * input argument that was set to {@code null} vs not provided at all. When
 * this wrapper type is used, the annotation is optional, and the name of the
 * argument is derived from the method parameter name.
 *
 * <p>An {@link ArgumentValue} can also be nested within the object structure
 * of an {@link Argument @Argument}-annotated method parameter.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 * @see org.springframework.graphql.data.method.annotation.Argument
 * @see org.springframework.graphql.data.method.annotation.Arguments
 * @see org.springframework.graphql.data.GraphQlArgumentBinder
 */
public class ArgumentMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final GraphQlArgumentBinder argumentBinder;


	public ArgumentMethodArgumentResolver(GraphQlArgumentBinder argumentBinder) {
		Assert.notNull(argumentBinder, "GraphQlArgumentBinder is required");
		this.argumentBinder = argumentBinder;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.getParameterAnnotation(Argument.class) != null ||
				parameter.getParameterType() == ArgumentValue.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		String name = getArgumentName(parameter);
		ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
		return this.argumentBinder.bind(environment, name, resolvableType);
	}

	static String getArgumentName(MethodParameter parameter) {
		Argument argument = parameter.getParameterAnnotation(Argument.class);
		if (argument != null) {
			if (StringUtils.hasText(argument.name())) {
				return argument.name();
			}
		}
		else if (parameter.getParameterType() != ArgumentValue.class) {
			throw new IllegalStateException("Expected @Argument annotation");
		}

		String parameterName = parameter.getParameterName();
		if (parameterName != null) {
			return parameterName;
		}

		throw new IllegalArgumentException(
				"Name for argument of type [" + parameter.getNestedParameterType().getName() +
						"] not specified, and parameter name information not found in class file either.");
	}

}

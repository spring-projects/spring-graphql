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
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Argument;
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
 * @see Argument
 */
public class ArgumentMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final GraphQlArgumentBinder argumentInitializer;


	public ArgumentMethodArgumentResolver(GraphQlArgumentBinder initializer) {
		Assert.notNull(initializer, "GraphQlArgumentInitializer is required");
		this.argumentInitializer = initializer;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.getParameterAnnotation(Argument.class) != null);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		String name = getArgumentName(parameter);
		ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
		return this.argumentInitializer.bind(environment, name, resolvableType);
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

}

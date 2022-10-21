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

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Arguments;
import org.springframework.util.Assert;

/**
 * Resolver for a method parameter that is annotated with
 * {@link Arguments @Arguments}, similar to what
 * {@link ArgumentMethodArgumentResolver} does but using the full
 * full {@link DataFetchingEnvironment#getArgument(String) GraphQL arguments}
 * map as the source for binding to the target Object rather than a specific
 * argument value within it.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see org.springframework.graphql.data.method.annotation.Arguments
 * @see org.springframework.graphql.data.method.annotation.Argument
 * @see org.springframework.graphql.data.GraphQlArgumentBinder
 */
public class ArgumentsMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final GraphQlArgumentBinder argumentBinder;


	public ArgumentsMethodArgumentResolver(GraphQlArgumentBinder argumentBinder) {
		Assert.notNull(argumentBinder, "GraphQlArgumentBinder is required");
		this.argumentBinder = argumentBinder;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterAnnotation(Arguments.class) != null;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
		return this.argumentBinder.bind(environment, null, resolvableType);
	}

}

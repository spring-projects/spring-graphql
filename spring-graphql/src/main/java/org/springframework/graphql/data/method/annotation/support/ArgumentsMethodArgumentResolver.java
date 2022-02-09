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
import org.springframework.graphql.data.GraphQlArgumentInitializer;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.Arguments;
import org.springframework.util.Assert;

/**
 * Resolver for {@link Arguments @Arguments} annotated method parameters,
 * obtained via {@link DataFetchingEnvironment#getArgument(String)} and
 * converted to the declared type of the method parameter.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ArgumentsMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final GraphQlArgumentInitializer argumentInitializer;


	public ArgumentsMethodArgumentResolver(GraphQlArgumentInitializer initializer) {
		Assert.notNull(initializer, "GraphQlArgumentInitializer is required");
		this.argumentInitializer = initializer;
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterAnnotation(Arguments.class) != null;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) throws Exception {
		ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
		return this.argumentInitializer.initializeArgument(environment, null, resolvableType);
	}

}

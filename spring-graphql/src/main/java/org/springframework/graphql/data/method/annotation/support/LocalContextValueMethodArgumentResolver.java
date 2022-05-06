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

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.LocalContextValue;
import org.springframework.util.Assert;


/**
 * Resolver for a {@link LocalContextValue @LocalContextValue} annotated method
 * parameter.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class LocalContextValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.getParameterAnnotation(LocalContextValue.class) != null);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {

		LocalContextValue annotation = parameter.getParameterAnnotation(LocalContextValue.class);
		Assert.state(annotation != null, "Expected @LocalContextValue annotation");
		String name = ContextValueMethodArgumentResolver.getContextValueName(parameter, annotation.name(), annotation);

		Object localContext = environment.getLocalContext();
		Assert.state(localContext == null || localContext instanceof GraphQLContext,
				"Local context is not an instance of  graphql.GraphQLContext");

		return ContextValueMethodArgumentResolver.resolveContextValue(
				name, annotation.required(), parameter, (GraphQLContext) localContext);
	}


}

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

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;

/**
 * Resolver for {@link DataFetchingEnvironment} or {@link GraphQLContext} arguments.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DataFetchingEnvironmentMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> type = parameter.getParameterType();
		return (type.equals(DataFetchingEnvironment.class) || type.equals(GraphQLContext.class));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {
		Class<?> type = parameter.getParameterType();
		if (type.equals(GraphQLContext.class)) {
			return environment.getGraphQlContext();
		}
		else {
			return environment;
		}
	}

}

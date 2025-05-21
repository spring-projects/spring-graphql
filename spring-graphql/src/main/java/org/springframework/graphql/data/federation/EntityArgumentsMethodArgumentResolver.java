/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.data.federation;

import java.util.List;
import java.util.Map;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.federation.EntityArgumentMethodArgumentResolver.EntityBatchDataFetchingEnvironment;
import org.springframework.graphql.data.federation.EntityArgumentMethodArgumentResolver.EntityDataFetchingEnvironment;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.util.Assert;

/**
 * Resolver that exposes the raw representation input {@link Map},
 * or {@link List} of maps for batched invocations.
 *
 * @author Rossen Stoyanchev
 */
final class EntityArgumentsMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final GraphQlArgumentBinder argumentBinder;


	EntityArgumentsMethodArgumentResolver(GraphQlArgumentBinder argumentBinder) {
		Assert.notNull(argumentBinder, "GraphQlArgumentBinder is required");
		this.argumentBinder = argumentBinder;
	}


	@Override
	public boolean supportsParameter(MethodParameter param) {
		Class<?> type = param.getParameterType();
		if (List.class.isAssignableFrom(param.getParameterType())) {
			type = param.nested().getNestedParameterType();
			if (Object.class.equals(type)) {
				// Maybe a Kotlin List
				type = ResolvableType.forMethodParameter(param).getNested(2).resolve(Object.class);
			}
		}
		return Map.class.isAssignableFrom(type);
	}

	@Override
	public @Nullable Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment env) throws Exception {
		ResolvableType targetType = ResolvableType.forMethodParameter(parameter);
		if (env instanceof EntityDataFetchingEnvironment entityEnv) {
			return this.argumentBinder.bind(entityEnv.getRepresentation(), false, targetType);
		}
		else if (env instanceof EntityBatchDataFetchingEnvironment batchEnv) {
			return this.argumentBinder.bind(batchEnv.getRepresentations(), false, targetType);
		}
		else {
			throw new IllegalStateException("Expected decorated DataFetchingEnvironment");
		}
	}

}

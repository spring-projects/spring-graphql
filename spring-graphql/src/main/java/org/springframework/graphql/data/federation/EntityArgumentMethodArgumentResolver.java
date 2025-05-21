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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DelegatingDataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.core.ResolvableType;
import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.support.ArgumentMethodArgumentResolver;
import org.springframework.validation.BindException;

/**
 * Resolver for a method parameter annotated with {@link Argument @Argument}.
 * On {@code @EntityMapping} methods, the raw argument value is obtained from
 * the "representation" input map for the entity with entries that identify
 * the entity uniquely.
 *
 * @author Rossen Stoyanchev
 */
final class EntityArgumentMethodArgumentResolver extends ArgumentMethodArgumentResolver {


	EntityArgumentMethodArgumentResolver(GraphQlArgumentBinder argumentBinder) {
		super(argumentBinder);
	}


	@Override
	protected @Nullable Object doBind(
			DataFetchingEnvironment environment, String name, ResolvableType targetType) throws BindException {

		if (environment instanceof EntityDataFetchingEnvironment entityEnv) {
			return doBind(name, targetType, entityEnv.getRepresentation());
		}
		else if (environment instanceof EntityBatchDataFetchingEnvironment batchEnv) {
			name = dePluralize(name);
			targetType = targetType.getNested(2);
			List<@Nullable Object> values = new ArrayList<>();
			for (Map<String, Object> representation : batchEnv.getRepresentations()) {
				values.add(doBind(name, targetType, representation));
			}
			return values;
		}
		else {
			throw new IllegalStateException("Expected decorated DataFetchingEnvironment");
		}
	}

	private @Nullable Object doBind(String name, ResolvableType targetType, Map<String, Object> entityMap) throws BindException {
		Object rawValue = entityMap.get(name);
		boolean isOmitted = !entityMap.containsKey(name);
		return getArgumentBinder().bind(rawValue, isOmitted, targetType);
	}

	private static String dePluralize(String name) {
		return (name.endsWith("List")) ? name.substring(0, name.length() - 4) : name;
	}


	/**
	 * Utility method for use from {@link EntityHandlerMethod} to make the entity
	 * representation map available.
	 */
	static DataFetchingEnvironment wrap(DataFetchingEnvironment env, Map<String, Object> representation) {
		return new EntityDataFetchingEnvironment(env, representation);
	}

	/**
	 * Utility method for use from {@link EntityHandlerMethod} to make the list
	 * of entity representation maps available.
	 */
	static DataFetchingEnvironment wrap(DataFetchingEnvironment env, List<Map<String, Object>> representations) {
		return new EntityBatchDataFetchingEnvironment(env, representations);
	}


	/**
	 * Wrap the DataFetchingEnvironment to also make the representation map available.
	 */
	static class EntityDataFetchingEnvironment extends DelegatingDataFetchingEnvironment {

		private final Map<String, Object> representation;

		EntityDataFetchingEnvironment(DataFetchingEnvironment env, Map<String, Object> representation) {
			super(env);
			this.representation = representation;
		}

		Map<String, Object> getRepresentation() {
			return this.representation;
		}
	}


	/**
	 * Wrap the DataFetchingEnvironment to also make representation maps available
	 * for batched invocations.
	 */
	static class EntityBatchDataFetchingEnvironment extends DelegatingDataFetchingEnvironment {

		private final List<Map<String, Object>> representations;

		EntityBatchDataFetchingEnvironment(DataFetchingEnvironment env, List<Map<String, Object>> representations) {
			super(env);
			this.representations = representations;
		}

		List<Map<String, Object>> getRepresentations() {
			return this.representations;
		}
	}

}

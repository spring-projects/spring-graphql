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
package org.springframework.graphql.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link TypeResolver} that tries to find a GraphQL Object type based on the
 * class name of a value returned from a {@code DataFetcher}. If necessary, it
 * walks up the base class and interface hierarchy to find a match.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ClassNameTypeResolver implements TypeResolver {

	private Function<Class<?>, String> classNameExtractor = Class::getSimpleName;

	private final Map<Class<?>, String> mappings = new LinkedHashMap<>();


	/**
	 * Customize how the name of a class, or a base class/interface, is determined.
	 * An application can use this to adapt to a common naming convention, e.g.
	 * remove an "Impl" suffix or a "Base" prefix, and so on.
	 * <p>By default, this is just {@link Class#getSimpleName()}.
	 * @param classNameExtractor the function to use
	 */
	public void setClassNameExtractor(Function<Class<?>, String> classNameExtractor) {
		Assert.notNull(classNameExtractor, "'classNameExtractor' is required");
		this.classNameExtractor = classNameExtractor;
	}

	/**
	 * Add a mapping from a Java {@link Class} to a GraphQL Object type name.
	 * The mapping applies to the given type and to all of its sub-classes
	 * (for a base class) or implementations (for an interface).
	 * @param clazz the Java class to map
	 * @param graphQlTypeName the matching GraphQL object type
	 */
	public void addMapping(Class<?> clazz, String graphQlTypeName) {
		this.mappings.put(clazz, graphQlTypeName);
	}


	@Override
	@Nullable
	public GraphQLObjectType getType(TypeResolutionEnvironment environment) {

		Class<?> clazz = environment.getObject().getClass();
		GraphQLSchema schema = environment.getSchema();

		// We don't assert "not null" since GraphQL Java will do that anyway.
		// Leaving the method nullable provides option for delegation.

		return getTypeForClass(clazz, schema);
	}

	@Nullable
	private GraphQLObjectType getTypeForClass(Class<?> clazz, GraphQLSchema schema) {
		if (clazz.getName().startsWith("java.")) {
			return null;
		}

		String name = getMapping(clazz);
		if (name != null) {
			GraphQLObjectType objectType = schema.getObjectType(name);
			if (objectType == null) {
				throw new IllegalStateException(
						"Invalid mapping for " + clazz.getName() + ". " +
								"No GraphQL Object type with name '" + name + "'.");
			}
			return objectType;
		}

		name = this.classNameExtractor.apply(clazz);
		if (schema.containsType(name)) {
			return schema.getObjectType(name);
		}

		for (Class<?> interfaceType : clazz.getInterfaces()) {
			GraphQLObjectType objectType = getTypeForClass(interfaceType, schema);
			if (objectType != null) {
				return objectType;
			}
		}

		Class<?> superclass = clazz.getSuperclass();
		if (superclass != Object.class && superclass != null) {
			return getTypeForClass(superclass, schema);
		}

		return null;
	}

	@Nullable
	private String getMapping(Class<?> targetClass) {
		for (Map.Entry<Class<?>, String> entry : this.mappings.entrySet()) {
			if (entry.getKey().isAssignableFrom(targetClass)) {
				return entry.getValue();
			}
		}
		return null;
	}

}

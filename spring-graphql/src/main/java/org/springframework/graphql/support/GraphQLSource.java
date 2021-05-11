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
package org.springframework.graphql.support;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

/**
 * Strategy to resolve the {@link GraphQL} instance to use.
 *
 * <p>This contract also includes a {@link GraphQLSource} builder encapsulating
 * the initialization of the {@link GraphQL} instance and associated
 * {@link graphql.schema.GraphQLSchema}.
 */
public interface GraphQLSource {


	/**
	 * Return the {@link GraphQL} to use. This can be a cached instance or a
	 * different one from time to time (e.g. based on a reloaded schema).
	 */
	GraphQL graphQL();

	/**
	 * Return the {@link GraphQLSchema} used by the current {@link GraphQL}.
	 */
	GraphQLSchema schema();


	/**
	 * Return a builder for a {@link GraphQLSource} given input for the
	 * initialization of {@link GraphQL} and {@link graphql.schema.GraphQLSchema}.
	 */
	static Builder builder() {
		return new DefaultGraphQLSourceBuilder();
	}


	/**
	 * Builder for a {@link GraphQLSource}.
	 */
	interface Builder {

		/**
		 * Provide the resource for the GraphQL {@literal ".schema"} file to parse.
		 * @see graphql.schema.idl.SchemaParser#parse(File)
		 */
		Builder schemaResource(Resource resource);

		/**
		 * Set a {@link RuntimeWiring} to contribute data fetchers and more.
		 * @see graphql.schema.idl.SchemaGenerator#makeExecutableSchema(TypeDefinitionRegistry, RuntimeWiring)
		 */
		Builder runtimeWiring(RuntimeWiring runtimeWiring);

		/**
		 * Add {@link GraphQLTypeVisitor}s to transform the underlying
		 * {@link graphql.schema.GraphQLSchema} with.
		 * @see graphql.schema.SchemaTransformer#transformSchema(GraphQLSchema, GraphQLTypeVisitor)
		 */
		Builder typeVisitors(List<GraphQLTypeVisitor> typeVisitors);

		/**
		 * Provide {@link Instrumentation} components to instrument the execution
		 * of GraphQL queries.
		 * @see graphql.GraphQL.Builder#instrumentation(Instrumentation)
		 */
		Builder instrumentation(List<Instrumentation> instrumentations);

		/**
		 * Configure consumers to be given access to the {@link GraphQL.Builder}
		 * used to build {@link GraphQL}.
		 */
		Builder configureGraphQL(Consumer<GraphQL.Builder> configurer);

		/**
		 * Build the {@link GraphQLSource}.
		 */
		GraphQLSource build();
	}

}

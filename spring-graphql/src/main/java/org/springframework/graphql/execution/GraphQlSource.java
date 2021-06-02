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

/**
 * Strategy to resolve the {@link GraphQL} instance to use.
 *
 * <p>
 * This contract also includes a {@link GraphQlSource} builder encapsulating the
 * initialization of the {@link GraphQL} instance and associated
 * {@link graphql.schema.GraphQLSchema}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public interface GraphQlSource {

	/**
	 * Return the {@link GraphQL} to use. This can be a cached instance or a different one
	 * from time to time (e.g. based on a reloaded schema).
	 * @return the GraphQL instance to use
	 */
	GraphQL graphQl();

	/**
	 * Return the {@link GraphQLSchema} used by the current {@link GraphQL}.
	 * @return the current GraphQL schema
	 */
	GraphQLSchema schema();

	/**
	 * Return a builder for a {@link GraphQlSource} given input for the initialization of
	 * {@link GraphQL} and {@link graphql.schema.GraphQLSchema}.
	 * @return a builder for a GraphQlSource
	 */
	static Builder builder() {
		return new DefaultGraphQlSourceBuilder();
	}

	/**
	 * Builder for a {@link GraphQlSource}.
	 */
	interface Builder {

		/**
		 * Provide the resource for the GraphQL {@literal ".schema"} file to parse.
		 * @param resource the resource for the GraphQL schema
		 * @return the current builder
		 * @see graphql.schema.idl.SchemaParser#parse(File)
		 */
		Builder schemaResource(Resource resource);

		/**
		 * Set a {@link RuntimeWiring} to contribute data fetchers and more.
		 * @param runtimeWiring the runtime wiring for contribution
		 * @return the current builder
		 * @see graphql.schema.idl.SchemaGenerator#makeExecutableSchema(TypeDefinitionRegistry,
		 * RuntimeWiring)
		 */
		Builder runtimeWiring(RuntimeWiring runtimeWiring);

		/**
		 * Add {@link DataFetcherExceptionResolver}'s to use for resolving exceptions from
		 * {@link graphql.schema.DataFetcher}'s.
		 * @param resolvers the resolvers to add
		 * @return the current builder
		 */
		Builder exceptionResolvers(List<DataFetcherExceptionResolver> resolvers);

		/**
		 * Add {@link GraphQLTypeVisitor}'s to transform the underlying
		 * {@link graphql.schema.GraphQLSchema} with.
		 * @param typeVisitors the type visitors
		 * @return the current builder
		 * @see graphql.schema.SchemaTransformer#transformSchema(GraphQLSchema,
		 * GraphQLTypeVisitor)
		 */
		Builder typeVisitors(List<GraphQLTypeVisitor> typeVisitors);

		/**
		 * Provide {@link Instrumentation} components to instrument the execution of
		 * GraphQL queries.
		 * @param instrumentations the instrumentation components
		 * @return the current builder
		 * @see graphql.GraphQL.Builder#instrumentation(Instrumentation)
		 */
		Builder instrumentation(List<Instrumentation> instrumentations);

		/**
		 * Configure consumers to be given access to the {@link GraphQL.Builder} used to
		 * build {@link GraphQL}.
		 * @param configurer the configurer
		 * @return the current builder
		 */
		Builder configureGraphQl(Consumer<GraphQL.Builder> configurer);

		/**
		 * Build the {@link GraphQlSource}.
		 * @return the built GraphQlSource
		 */
		GraphQlSource build();

	}

}

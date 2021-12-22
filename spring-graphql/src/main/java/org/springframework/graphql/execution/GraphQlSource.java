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
import java.util.function.BiFunction;
import java.util.function.Consumer;

import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.core.io.Resource;
import org.springframework.graphql.execution.preparsed.SpringNoOpPreparsedDocumentProvider;

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
		 * Add {@literal ".graphqls"} schema resources to be
		 * {@link TypeDefinitionRegistry#merge(TypeDefinitionRegistry) merged} into the type registry.
		 * @param resources resources for the GraphQL schema
		 * @return the current builder
		 * @see graphql.schema.idl.SchemaParser#parse(File)
		 */
		Builder schemaResources(Resource... resources);

		/**
		 * Add a component that is given access to the {@link RuntimeWiring.Builder}
		 * used to register {@link graphql.schema.DataFetcher}s, custom scalar
		 * types, type resolvers, and more.
		 * @param configurer the configurer to apply
		 * @return the current builder
		 * @see graphql.schema.idl.SchemaGenerator#makeExecutableSchema(TypeDefinitionRegistry, RuntimeWiring)
		 */
		Builder configureRuntimeWiring(RuntimeWiringConfigurer configurer);

		/**
		 * Configure the default {@link TypeResolver} to use for GraphQL Interface
		 * and Union types that don't already have such a registration after all
		 * {@link #configureRuntimeWiring(RuntimeWiringConfigurer) RuntimeWiringConfigurer's}
		 * have been applied.
		 * <p>A GraphQL {@code TypeResolver} is used to determine the GraphQL Object
		 * type of values returned from DataFetcher's of GraphQL Interface or
		 * Union fields.
		 * <p>By default this is set to {@link ClassNameTypeResolver}, which
		 * tries to match the simple class name of the Object value to a GraphQL
		 * Object type, and it also tries the same for supertypes (base classes
		 * and interfaces). See the Javadoc of {@code ClassNameTypeResolver} for
		 * further ways to customize matching a Java class to a GraphQL Object type.
		 * @param typeResolver the {@code TypeResolver} to use
		 * @return the current builder
		 * @see ClassNameTypeResolver
		 */
		Builder defaultTypeResolver(TypeResolver typeResolver);

		/**
		 * Configure the {@link PreparsedDocumentProvider} to use for GraphQL requests.
		 * <p>
		 * A {@code PreparsedDocumentProvider} can be used to cache and/or whitelist
		 * {@link Document} instances for queries. Configuring a
		 * {@code PreparsedDocumentProvider} gives you the ability to skip query parsing
		 * and validation.
		 * <p>
		 * By default, this is set to {@link SpringNoOpPreparsedDocumentProvider}, which
		 * calls the {@code parseAndValidateFunction}, and does nothing else.
		 * @param preparsedDocumentProvider the {@code PreparsedDocumentProvider} to use
		 * @return the current builder
		 * @see GraphQL#getPreparsedDocumentProvider()
		 */
		Builder preparsedDocumentProvider(PreparsedDocumentProvider preparsedDocumentProvider);

		/**
		 * Add {@link DataFetcherExceptionResolver}'s to use for resolving exceptions from
		 * {@link graphql.schema.DataFetcher}'s.
		 * @param resolvers the resolvers to add
		 * @return the current builder
		 */
		Builder exceptionResolvers(List<DataFetcherExceptionResolver> resolvers);

		/**
		 * Add {@link GraphQLTypeVisitor}s to visit all element of the created
		 * {@link graphql.schema.GraphQLSchema}.
		 * <p><strong>Note:</strong> Visitors are applied via
		 * {@link graphql.schema.SchemaTraverser} and cannot change the schema.
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
		 * Configure a function to create the {@link GraphQLSchema} instance from the
		 * given {@link TypeDefinitionRegistry} and {@link RuntimeWiring}. This may
		 * be useful for federation to create a combined schema.
		 * <p>By default, the schema is created with
		 * {@link graphql.schema.idl.SchemaGenerator#makeExecutableSchema}.
		 * @param schemaFactory the function to create the schema
		 * @return the current builder
		 */
		Builder schemaFactory(BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory);

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

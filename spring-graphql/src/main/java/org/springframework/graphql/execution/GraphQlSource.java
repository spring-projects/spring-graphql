/*
 * Copyright 2002-present the original author or authors.
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

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.core.io.Resource;


/**
 * Strategy to resolve a {@link GraphQL} and a {@link GraphQLSchema}.
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
	 * Return the {@link GraphQL} to use. This can be a cached instance or a
	 * different one from time to time (e.g. based on a reloaded schema).
	 */
	GraphQL graphQl();

	/**
	 * Return the {@link GraphQLSchema} used by the current {@link GraphQL}.
	 */
	GraphQLSchema schema();


	/**
	 * Return a {@link GraphQlSource} builder that parses GraphQL Schema
	 * resources and uses {@link RuntimeWiring} to create the
	 * {@link graphql.schema.GraphQLSchema}.
	 */
	static SchemaResourceBuilder schemaResourceBuilder() {
		return new DefaultSchemaResourceGraphQlSourceBuilder();
	}

	/**
	 * Return a {@link GraphQlSource} builder that uses an externally prepared
	 * {@link GraphQLSchema}.
	 * @param schema the GraphQL schema
	 */
	static Builder<?> builder(GraphQLSchema schema) {
		return new ExternalSchemaGraphQlSourceBuilder(schema);
	}



	/**
	 * Common configuration options for all {@link GraphQlSource} builders,
	 * independent of how {@link GraphQLSchema} is created.
	 * @param <B> the builder type
	 */
	interface Builder<B extends Builder<B>> {

		/**
		 * Add {@link DataFetcherExceptionResolver}'s that are invoked when a
		 * {@link graphql.schema.DataFetcher} raises an exception. Resolvers
		 * are invoked in sequence until one emits a list.
		 * @param resolvers the resolvers to add
		 * @return the current builder
		 */
		B exceptionResolvers(List<DataFetcherExceptionResolver> resolvers);

		/**
		 * Add {@link SubscriptionExceptionResolver}s that are invoked when a
		 * GraphQL subscription {@link org.reactivestreams.Publisher} ends with
		 * error, and given a chance to resolve the exception to one or more
		 * GraphQL errors to be sent to the client. Resolvers are invoked in
		 * sequence until one emits a list.
		 * @param resolvers the subscription exception resolver
		 * @return the current builder
		 * @since 1.0.1
		 */
		B subscriptionExceptionResolvers(List<SubscriptionExceptionResolver> resolvers);

		/**
		 * Add {@link GraphQLTypeVisitor}s to visit all element of the created
		 * {@link graphql.schema.GraphQLSchema} and make changes to the
		 * {@link graphql.schema.GraphQLCodeRegistry}.
		 * <p><strong>Note:</strong> Visitors are applied via
		 * {@link graphql.schema.SchemaTraverser} and cannot change the schema.
		 * @param typeVisitors the type visitors
		 * @return the current builder
		 * @see graphql.schema.SchemaTransformer#transformSchema(GraphQLSchema, GraphQLTypeVisitor)
		 */
		B typeVisitors(List<GraphQLTypeVisitor> typeVisitors);

		/**
		 * Alternative to {@link #typeVisitors(List)} for visitors that also
		 * need to make schema changes.
		 * <p><strong>Note:</strong> Visitors are applied via
		 * {@link graphql.schema.SchemaTransformer}, and therefore can change
		 * the schema. However, this is more expensive than using
		 * {@link graphql.schema.SchemaTraverser}, so generally prefer
		 * {@link #typeVisitors(List)} if it's not necessary to change the schema.
		 * @param typeVisitors the type visitors to register
		 * @return the current builder
		 * @since 1.1.0
		 * @see graphql.schema.SchemaTransformer#transformSchema(GraphQLSchema, GraphQLTypeVisitor)
		 */
		B typeVisitorsToTransformSchema(List<GraphQLTypeVisitor> typeVisitors);

		/**
		 * Provide {@link Instrumentation} components to instrument the
		 * execution of GraphQL queries.
		 * @param instrumentations the instrumentation components
		 * @return the current builder
		 * @see graphql.GraphQL.Builder#instrumentation(Instrumentation)
		 */
		B instrumentation(List<Instrumentation> instrumentations);

		/**
		 * Configure consumers to be given access to the {@link GraphQL.Builder}
		 * used to build {@link GraphQL}.
		 * @param configurer the configurer
		 * @return the current builder
		 */
		B configureGraphQl(Consumer<GraphQL.Builder> configurer);

		/**
		 * Configure a factory to use to create the {@link GraphQlSource} instance
		 * to return from the {@link #build()} method.
		 * <p>By default, the instance is a simple container of {@link GraphQL} and
		 * {@link GraphQLSchema}. Applications can use this to create a different
		 * implementation that applies additional per-request logic.
		 * @param factory the factory to use
		 * @return the current builder
		 * @since 1.4.0
		 */
		B graphQlSourceFactory(Factory factory);

		/**
		 * Build the {@link GraphQlSource} instance.
		 */
		GraphQlSource build();

	}


	/**
	 * {@link GraphQlSource} builder that relies on parsing schema definition
	 * files and uses a {@link RuntimeWiring} to create the underlying
	 * {@link GraphQLSchema}.
	 */
	interface SchemaResourceBuilder extends Builder<SchemaResourceBuilder> {

		/**
		 * Add schema definition resources, typically {@literal ".graphqls"} files, to be
		 * {@link graphql.schema.idl.SchemaParser#parse(java.io.InputStream) parsed} and
		 * {@link TypeDefinitionRegistry#merge(TypeDefinitionRegistry) merged}.
		 * @param resources resources with GraphQL schema definitions
		 * @return the current builder
		 */
		SchemaResourceBuilder schemaResources(Resource... resources);

		/**
		 * Customize the {@link TypeDefinitionRegistry} created from parsed
		 * schema files, adding or changing schema type definitions before the
		 * {@link GraphQLSchema} is created and validated.
		 * @param configurer the configurer to apply
		 * @return the current builder
		 * @since 1.2.0
		 */
		SchemaResourceBuilder configureTypeDefinitions(TypeDefinitionConfigurer configurer);

		/**
		 * Configure the underlying {@link RuntimeWiring.Builder} to register
		 * data fetchers, custom scalar types, type resolvers, and more.
		 * @param configurer the configurer to apply
		 * @return the current builder
		 */
		SchemaResourceBuilder configureRuntimeWiring(RuntimeWiringConfigurer configurer);

		/**
		 * Configure the default {@link TypeResolver} to use for GraphQL interface
		 * and union types that don't have such a registration after
		 * {@link #configureRuntimeWiring(RuntimeWiringConfigurer) applying}
		 * {@code RuntimeWiringConfigurer}s.
		 * <p>By default this is set to {@link ClassNameTypeResolver}.
		 * @param typeResolver the {@code TypeResolver} to use
		 * @return the current builder
		 */
		SchemaResourceBuilder defaultTypeResolver(TypeResolver typeResolver);

		/**
		 * Enable inspection of schema mappings to find unmapped fields and
		 * unmapped {@code DataFetcher} registrations. For more details, see
		 * {@link SchemaReport} and the reference documentation.
		 * @param reportConsumer a hook to inspect the report
		 * @return the current builder
		 * @since 1.2.0
		 */
		SchemaResourceBuilder inspectSchemaMappings(Consumer<SchemaReport> reportConsumer);

		/**
		 * Variant of {@link #inspectSchemaMappings(Consumer)} with the option to
		 * initialize the {@link SchemaMappingInspector}, e.g. in order to assist
		 * with finding Java representations of GraphQL union member types and
		 * interface implementation types.
		 * @param initializerConsumer callback to initialize the {@code SchemaMappingInspector}
		 * @param reportConsumer a hook to inspect the report
		 * @return the current builder
		 * @since 1.3.0
		 */
		SchemaResourceBuilder inspectSchemaMappings(
				Consumer<SchemaMappingInspector.Initializer> initializerConsumer,
				Consumer<SchemaReport> reportConsumer);

		/**
		 * Configure a function to create the {@link GraphQLSchema} from the
		 * given {@link TypeDefinitionRegistry} and {@link RuntimeWiring}.
		 * This may be used for federation to create a combined schema.
		 * <p>By default, the schema is created with
		 * {@link graphql.schema.idl.SchemaGenerator#makeExecutableSchema}.
		 * @param schemaFactory the function to create the schema
		 * @return the current builder
		 */
		SchemaResourceBuilder schemaFactory(
				BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory);

	}


	/**
	 * Strategy to create the {@link GraphQlSource} instance in {@link Builder#build()}.
	 */
	interface Factory {

		/**
		 * Create a {@link GraphQlSource} with the given inputs.
		 * @param graphQl the {@code GraphQLJava} initialized by the builder
		 * @param schema the schema initialized by the builder
		 * @return the created instance
		 */
		GraphQlSource create(GraphQL graphQl, GraphQLSchema schema);
	}

}

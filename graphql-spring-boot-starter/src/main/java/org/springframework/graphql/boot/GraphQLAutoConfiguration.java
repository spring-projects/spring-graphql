/*
 * Copyright 2020-2021 the original author or authors.
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
package org.springframework.graphql.boot;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.SchemaTransformer;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.graphql.core.ReactorDataFetcherAdapter;

@Configuration
@ConditionalOnClass(GraphQL.class)
@ConditionalOnMissingBean(GraphQL.class)
@EnableConfigurationProperties(GraphQLProperties.class)
public class GraphQLAutoConfiguration {


	@Configuration
	static class GraphQLConfiguration {

		@Bean
		public GraphQL graphQL(GraphQL.Builder builder) {
			return builder.build();
		}

	}


	@Configuration
	@ConditionalOnMissingBean(GraphQL.Builder.class)
	static class GraphQLBuilderConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public RuntimeWiring runtimeWiring(ObjectProvider<RuntimeWiringCustomizer> customizers) {
			RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
			customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
			return builder.build();
		}

		@Bean
		public GraphQL.Builder graphQLBuilder(GraphQLProperties properties, RuntimeWiring runtimeWiring,
				ResourceLoader resourceLoader,
				ObjectProvider<Instrumentation> instrumentationsProvider) {

			Resource schemaResource = resourceLoader.getResource(properties.getSchemaLocation());
			GraphQLSchema schema = buildSchema(schemaResource, runtimeWiring);
			schema = SchemaTransformer.transformSchema(schema, ReactorDataFetcherAdapter.TYPE_VISITOR);

			GraphQL.Builder builder = GraphQL.newGraphQL(schema);
			List<Instrumentation> instrumentations = instrumentationsProvider.orderedStream().collect(Collectors.toList());
			if (!instrumentations.isEmpty()) {
				builder = builder.instrumentation(new ChainedInstrumentation(instrumentations));
			}
			return builder;
		}

		private GraphQLSchema buildSchema(Resource schemaResource, RuntimeWiring runtimeWiring) {
			if (!schemaResource.exists()) {
				throw new MissingGraphQLSchemaException(schemaResource);
			}
			try {
				TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaResource.getInputStream());
				SchemaGenerator schemaGenerator = new SchemaGenerator();
				return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
			}
			catch (IOException exc) {
				throw new MissingGraphQLSchemaException(exc, schemaResource);
			}
		}

	}

}

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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.SchemaTransformer;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import org.springframework.core.io.Resource;
import org.springframework.graphql.DataFetcherExceptionResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link GraphQLSource.Builder} that initializes a
 * {@link GraphQL} instance and wraps it with a {@link GraphQLSource} that
 * returns it.
 */
class DefaultGraphQLSourceBuilder implements GraphQLSource.Builder {

	@Nullable
	private Resource schemaResource;

	private RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().build();

	private final List<DataFetcherExceptionResolver> exceptionResolvers = new ArrayList<>();

	private final List<GraphQLTypeVisitor> typeVisitors = new ArrayList<>();

	private final List<Instrumentation> instrumentations = new ArrayList<>();

	private Consumer<GraphQL.Builder> graphQLConfigurers = builder -> {};


	DefaultGraphQLSourceBuilder() {
		this.typeVisitors.add(ReactorDataFetcherAdapter.TYPE_VISITOR);
	}


	@Override
	public GraphQLSource.Builder schemaResource(Resource resource) {
		this.schemaResource = resource;
		return this;
	}

	@Override
	public GraphQLSource.Builder runtimeWiring(RuntimeWiring runtimeWiring) {
		Assert.notNull(runtimeWiring, "RuntimeWiring is required");
		this.runtimeWiring = runtimeWiring;
		return this;
	}

	@Override
	public GraphQLSource.Builder exceptionResolvers(List<DataFetcherExceptionResolver> resolvers) {
		this.exceptionResolvers.addAll(resolvers);
		return this;
	}

	@Override
	public GraphQLSource.Builder typeVisitors(List<GraphQLTypeVisitor> typeVisitors) {
		this.typeVisitors.addAll(typeVisitors);
		return this;
	}

	@Override
	public GraphQLSource.Builder instrumentation(List<Instrumentation> instrumentations) {
		this.instrumentations.addAll(instrumentations);
		return this;
	}

	@Override
	public GraphQLSource.Builder configureGraphQL(Consumer<GraphQL.Builder> configurer) {
		this.graphQLConfigurers = this.graphQLConfigurers.andThen(configurer);
		return this;
	}

	@Override
	public GraphQLSource build() {
		TypeDefinitionRegistry registry = parseSchemaResource();

		GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, this.runtimeWiring);
		for (GraphQLTypeVisitor visitor : this.typeVisitors) {
			schema = SchemaTransformer.transformSchema(schema, visitor);
		}

		GraphQL.Builder builder = GraphQL.newGraphQL(schema);
		builder.defaultDataFetcherExceptionHandler(new ExceptionResolversExceptionHandler(this.exceptionResolvers));
		if (!this.instrumentations.isEmpty()) {
			builder = builder.instrumentation(new ChainedInstrumentation(this.instrumentations));
		}
		this.graphQLConfigurers.accept(builder);
		GraphQL graphQL = builder.build();

		return new CachedGraphQLSource(graphQL, schema);
	}

	private TypeDefinitionRegistry parseSchemaResource() {
		Assert.notNull(this.schemaResource, "'schemaResource' not provided");
		Assert.isTrue(this.schemaResource.exists(), "'schemaResource' does not exist");
		try {
			try (InputStream inputStream = this.schemaResource.getInputStream()) {
				return new SchemaParser().parse(inputStream);
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(
					"Failed to load resourceLocation " + this.schemaResource.toString());
		}
	}


	/**
	 * GraphQLSource that returns the built GraphQL instance and its schema.
	 */
	private static class CachedGraphQLSource implements GraphQLSource {

		private final GraphQL graphQL;

		private final GraphQLSchema schema;

		CachedGraphQLSource(GraphQL graphQL, GraphQLSchema schema) {
			this.graphQL = graphQL;
			this.schema = schema;
		}

		@Override
		public GraphQL graphQL() {
			return this.graphQL;
		}

		@Override
		public GraphQLSchema schema() {
			return this.schema;
		}
	}

}

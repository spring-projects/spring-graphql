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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.util.Assert;

/**
 * Default implementation of {@link GraphQlSource.Builder} that initializes a
 * {@link GraphQL} instance and wraps it with a {@link GraphQlSource} that returns it.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
class DefaultGraphQlSourceBuilder implements GraphQlSource.Builder {

	private List<Resource> schemaResources = new ArrayList<>();

	private RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().build();

	private final List<DataFetcherExceptionResolver> exceptionResolvers = new ArrayList<>();

	private final List<GraphQLTypeVisitor> typeVisitors = new ArrayList<>();

	private final List<Instrumentation> instrumentations = new ArrayList<>();

	private Consumer<GraphQL.Builder> graphQlConfigurers = (builder) -> {
	};

	DefaultGraphQlSourceBuilder() {
		this.typeVisitors.add(ContextDataFetcherDecorator.TYPE_VISITOR);
	}

	@Override
	public GraphQlSource.Builder schemaResources(Resource... resources) {
		this.schemaResources.addAll(Arrays.asList(resources));
		return this;
	}

	@Override
	public GraphQlSource.Builder runtimeWiring(RuntimeWiring runtimeWiring) {
		Assert.notNull(runtimeWiring, "RuntimeWiring is required");
		this.runtimeWiring = runtimeWiring;
		return this;
	}

	@Override
	public GraphQlSource.Builder exceptionResolvers(List<DataFetcherExceptionResolver> resolvers) {
		this.exceptionResolvers.addAll(resolvers);
		return this;
	}

	@Override
	public GraphQlSource.Builder typeVisitors(List<GraphQLTypeVisitor> typeVisitors) {
		this.typeVisitors.addAll(typeVisitors);
		return this;
	}

	@Override
	public GraphQlSource.Builder instrumentation(List<Instrumentation> instrumentations) {
		this.instrumentations.addAll(instrumentations);
		return this;
	}

	@Override
	public GraphQlSource.Builder configureGraphQl(Consumer<GraphQL.Builder> configurer) {
		this.graphQlConfigurers = this.graphQlConfigurers.andThen(configurer);
		return this;
	}

	@Override
	public GraphQlSource build() {
		TypeDefinitionRegistry registry = this.schemaResources.stream()
				.map(this::parseSchemaResource).reduce(TypeDefinitionRegistry::merge)
				.orElseThrow(() -> new IllegalArgumentException("'schemaResources' should not be empty"));

		GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, this.runtimeWiring);
		for (GraphQLTypeVisitor visitor : this.typeVisitors) {
			schema = SchemaTransformer.transformSchema(schema, visitor);
		}

		GraphQL.Builder builder = GraphQL.newGraphQL(schema);
		builder.defaultDataFetcherExceptionHandler(new ExceptionResolversExceptionHandler(this.exceptionResolvers));
		if (!this.instrumentations.isEmpty()) {
			builder = builder.instrumentation(new ChainedInstrumentation(this.instrumentations));
		}
		this.graphQlConfigurers.accept(builder);
		GraphQL graphQl = builder.build();

		return new CachedGraphQlSource(graphQl, schema);
	}

	private TypeDefinitionRegistry parseSchemaResource(Resource schemaResource) {
		Assert.notNull(schemaResource, "'schemaResource' not provided");
		Assert.isTrue(schemaResource.exists(), "'schemaResource' does not exist");
		try {
			try (InputStream inputStream = schemaResource.getInputStream()) {
				return new SchemaParser().parse(inputStream);
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Failed to load schema resource: " + schemaResource.toString());
		}
	}

	/**
	 * GraphQlSource that returns the built GraphQL instance and its schema.
	 */
	private static class CachedGraphQlSource implements GraphQlSource {

		private final GraphQL graphQl;

		private final GraphQLSchema schema;

		CachedGraphQlSource(GraphQL graphQl, GraphQLSchema schema) {
			this.graphQl = graphQl;
			this.schema = schema;
		}

		@Override
		public GraphQL graphQl() {
			return this.graphQl;
		}

		@Override
		public GraphQLSchema schema() {
			return this.schema;
		}

	}

}

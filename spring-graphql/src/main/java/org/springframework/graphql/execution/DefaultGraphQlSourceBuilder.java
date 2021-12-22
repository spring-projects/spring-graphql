/*
 * Copyright 2002-2022 the original author or authors.
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.SchemaTraverser;
import graphql.schema.TypeResolver;
import graphql.schema.idl.CombinedWiringFactory;
import graphql.schema.idl.NoopWiringFactory;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link GraphQlSource.Builder} that initializes a
 * {@link GraphQL} instance and wraps it with a {@link GraphQlSource} that returns it.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
class DefaultGraphQlSourceBuilder implements GraphQlSource.Builder {

	private final Set<Resource> schemaResources = new LinkedHashSet<>();

	private final List<RuntimeWiringConfigurer> runtimeWiringConfigurers = new ArrayList<>();

	@Nullable
	private TypeResolver defaultTypeResolver;

	private final List<DataFetcherExceptionResolver> exceptionResolvers = new ArrayList<>();

	private final List<GraphQLTypeVisitor> typeVisitors = new ArrayList<>();

	private final List<Instrumentation> instrumentations = new ArrayList<>();

	@Nullable
	private BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory;

	private Consumer<GraphQL.Builder> graphQlConfigurers = (builder) -> {
	};


	@Override
	public GraphQlSource.Builder schemaResources(Resource... resources) {
		this.schemaResources.addAll(Arrays.asList(resources));
		return this;
	}

	@Override
	public GraphQlSource.Builder configureRuntimeWiring(RuntimeWiringConfigurer configurer) {
		this.runtimeWiringConfigurers.add(configurer);
		return this;
	}

	@Override
	public GraphQlSource.Builder defaultTypeResolver(TypeResolver typeResolver) {
		this.defaultTypeResolver = typeResolver;
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
	public GraphQlSource.Builder schemaFactory(
			BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory) {

		this.schemaFactory = schemaFactory;
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
				.orElseThrow(MissingSchemaException::new);

		RuntimeWiring runtimeWiring = initRuntimeWiring();

		registerDefaultTypeResolver(registry, runtimeWiring);

		GraphQLSchema schema = (this.schemaFactory != null ?
				this.schemaFactory.apply(registry, runtimeWiring) :
				new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring));

		schema = applyTypeVisitors(schema);

		GraphQL.Builder builder = GraphQL.newGraphQL(schema);
		builder.defaultDataFetcherExceptionHandler(new ExceptionResolversExceptionHandler(this.exceptionResolvers));
		if (!this.instrumentations.isEmpty()) {
			builder = builder.instrumentation(new ChainedInstrumentation(this.instrumentations));
		}

		this.graphQlConfigurers.accept(builder);
		GraphQL graphQl = builder.build();

		return new CachedGraphQlSource(graphQl, schema);
	}

	private RuntimeWiring initRuntimeWiring() {
		RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
		this.runtimeWiringConfigurers.forEach(configurer -> configurer.configure(builder));

		List<WiringFactory> factories = new ArrayList<>();
		WiringFactory factory = builder.build().getWiringFactory();
		if (!factory.getClass().equals(NoopWiringFactory.class)) {
			factories.add(factory);
		}
		this.runtimeWiringConfigurers.forEach(configurer -> configurer.configure(builder, factories));
		if (!factories.isEmpty()) {
			builder.wiringFactory(new CombinedWiringFactory(factories));
		}

		return builder.build();
	}

	private void registerDefaultTypeResolver(TypeDefinitionRegistry registry, RuntimeWiring runtimeWiring) {
		TypeResolver typeResolver =
				(this.defaultTypeResolver != null ? this.defaultTypeResolver : new ClassNameTypeResolver());
		registry.types().values().stream()
				.filter(def -> def instanceof UnionTypeDefinition || def instanceof InterfaceTypeDefinition)
				.forEach(def -> runtimeWiring.getTypeResolvers().putIfAbsent(def.getName(), typeResolver));
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
			throw new IllegalArgumentException("Failed to load schema resource: " + schemaResource);
		}
	}

	private GraphQLSchema applyTypeVisitors(GraphQLSchema schema) {
		List<GraphQLTypeVisitor> visitors = new ArrayList<>(this.typeVisitors);
		visitors.add(ContextDataFetcherDecorator.TYPE_VISITOR);

		GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry(schema.getCodeRegistry());
		Map<Class<?>, Object> vars = Collections.singletonMap(GraphQLCodeRegistry.Builder.class, codeRegistry);

		SchemaTraverser traverser = new SchemaTraverser();
		traverser.depthFirstFullSchema(visitors, schema, vars);

		return schema.transformWithoutTypes(builder -> builder.codeRegistry(codeRegistry));
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

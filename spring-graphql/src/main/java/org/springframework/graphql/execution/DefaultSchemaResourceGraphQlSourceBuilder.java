/*
 * Copyright 2002-2023 the original author or authors.
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.CombinedWiringFactory;
import graphql.schema.idl.NoopWiringFactory;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;


/**
 * Implementation of {@link GraphQlSource.SchemaResourceBuilder}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
final class DefaultSchemaResourceGraphQlSourceBuilder
		extends AbstractGraphQlSourceBuilder<GraphQlSource.SchemaResourceBuilder>
		implements GraphQlSource.SchemaResourceBuilder {

	private static final Log logger = LogFactory.getLog(DefaultSchemaResourceGraphQlSourceBuilder.class);

	private final Set<Resource> schemaResources = new LinkedHashSet<>();

	private final List<TypeDefinitionConfigurer> typeDefinitionConfigurers = new ArrayList<>();

	private final List<RuntimeWiringConfigurer> runtimeWiringConfigurers = new ArrayList<>();


	@Nullable
	private TypeResolver typeResolver;

	@Nullable
	private BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory;

	@Nullable
	private Consumer<SchemaReport> schemaReportConsumer;


	@Override
	public DefaultSchemaResourceGraphQlSourceBuilder schemaResources(Resource... resources) {
		this.schemaResources.addAll(Arrays.asList(resources));
		return this;
	}

	@Override
	public GraphQlSource.SchemaResourceBuilder configureTypeDefinitions(TypeDefinitionConfigurer configurer) {
		this.typeDefinitionConfigurers.add(configurer);
		return this;
	}

	@Override
	public DefaultSchemaResourceGraphQlSourceBuilder configureRuntimeWiring(RuntimeWiringConfigurer configurer) {
		this.runtimeWiringConfigurers.add(configurer);
		return this;
	}

	@Override
	public DefaultSchemaResourceGraphQlSourceBuilder defaultTypeResolver(TypeResolver typeResolver) {
		this.typeResolver = typeResolver;
		return this;
	}

	@Override
	public GraphQlSource.SchemaResourceBuilder inspectSchemaMappings(Consumer<SchemaReport> consumer) {
		this.schemaReportConsumer = consumer;
		return this;
	}

	@Override
	public DefaultSchemaResourceGraphQlSourceBuilder schemaFactory(
			BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory) {

		this.schemaFactory = schemaFactory;
		return this;
	}

	@Override
	protected GraphQLSchema initGraphQlSchema() {

		TypeDefinitionRegistry registry = this.schemaResources.stream()
				.map(this::parse)
				.reduce(TypeDefinitionRegistry::merge)
				.orElseThrow(MissingSchemaException::new);

		for (TypeDefinitionConfigurer configurer : this.typeDefinitionConfigurers) {
			configurer.configure(registry);
		}

		logger.info("Loaded " + this.schemaResources.size() + " resource(s) in the GraphQL schema.");
		if (logger.isDebugEnabled()) {
			String resources = this.schemaResources.stream()
					.map(Resource::getDescription)
					.collect(Collectors.joining(","));
			logger.debug("Loaded GraphQL schema resources: (" + resources + ")");
		}

		RuntimeWiring runtimeWiring = initRuntimeWiring();

		TypeResolver typeResolver = initTypeResolver();
		registry.types().values().forEach(def -> {
			if (def instanceof UnionTypeDefinition || def instanceof InterfaceTypeDefinition) {
				runtimeWiring.getTypeResolvers().putIfAbsent(def.getName(), typeResolver);
			}
		});

		// SchemaMappingInspector needs RuntimeWiring, but cannot run here since type
		// visitors may transform the schema, for example to add Connection types.

		if (this.schemaReportConsumer != null) {
			configureGraphQl(builder -> {
				GraphQLSchema schema = builder.build().getGraphQLSchema();
				SchemaReport report = SchemaMappingInspector.inspect(schema, runtimeWiring);
				this.schemaReportConsumer.accept(report);
			});
		}

		return this.schemaFactory != null ?
				this.schemaFactory.apply(registry, runtimeWiring) :
				new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
	}

	private TypeDefinitionRegistry parse(Resource schemaResource) {
		Assert.notNull(schemaResource, "'schemaResource' not provided");
		Assert.isTrue(schemaResource.exists(), "'schemaResource' must exist: " + schemaResource);
		try {
			try (InputStream inputStream = schemaResource.getInputStream()) {
				return new SchemaParser().parse(inputStream);
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Failed to load schema resource: " + schemaResource);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to parse schema resource: " + schemaResource, ex);
		}
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

	private TypeResolver initTypeResolver() {
		return this.typeResolver != null ? this.typeResolver : new ClassNameTypeResolver();
	}

}

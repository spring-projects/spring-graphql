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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.SchemaTransformer;
import graphql.schema.SchemaTraverser;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;


/**
 * Implementation of {@link GraphQlSource.Builder} that leaves it to subclasses
 * to initialize {@link GraphQLSchema}.
 *
 * @param <B> the builder type
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public abstract class AbstractGraphQlSourceBuilder<B extends GraphQlSource.Builder<B>> implements GraphQlSource.Builder<B> {

	private final List<DataFetcherExceptionResolver> exceptionResolvers = new ArrayList<>();

	private final List<SubscriptionExceptionResolver> subscriptionExceptionResolvers = new ArrayList<>();

	private final List<GraphQLTypeVisitor> typeVisitors = new ArrayList<>();

	private final List<GraphQLTypeVisitor> typeVisitorsToTransformSchema = new ArrayList<>();

	private final List<Instrumentation> instrumentations = new ArrayList<>();

	private @Nullable Consumer<GraphQL.Builder> graphQlConfigurer;

	private GraphQlSource.Factory graphQlSourceFactory = FixedGraphQlSource::new;


	@Override
	public B exceptionResolvers(List<DataFetcherExceptionResolver> resolvers) {
		this.exceptionResolvers.addAll(resolvers);
		return self();
	}

	@Override
	public B subscriptionExceptionResolvers(List<SubscriptionExceptionResolver> resolvers) {
		this.subscriptionExceptionResolvers.addAll(resolvers);
		return self();
	}

	@Override
	public B typeVisitors(List<GraphQLTypeVisitor> typeVisitors) {
		this.typeVisitors.addAll(typeVisitors);
		return self();
	}

	@Override
	public B typeVisitorsToTransformSchema(List<GraphQLTypeVisitor> typeVisitorsToTransformSchema) {
		this.typeVisitorsToTransformSchema.addAll(typeVisitorsToTransformSchema);
		return self();
	}

	@Override
	public B instrumentation(List<Instrumentation> instrumentations) {
		this.instrumentations.addAll(instrumentations);
		return self();
	}

	@Override
	public B configureGraphQl(Consumer<GraphQL.Builder> configurer) {
		this.graphQlConfigurer = (this.graphQlConfigurer != null) ?
				this.graphQlConfigurer.andThen(configurer) : configurer;
		return self();
	}

	@Override
	public B graphQlSourceFactory(GraphQlSource.Factory factory) {
		Assert.notNull(factory, "GraphQlSource.Factory is required");
		this.graphQlSourceFactory = factory;
		return self();
	}

	@SuppressWarnings("unchecked")
	private  <T extends B> T self() {
		return (T) this;
	}

	@Override
	public GraphQlSource build() {
		GraphQLSchema schema = initGraphQlSchema();

		schema = applyTypeVisitorsToTransformSchema(schema);
		schema = applyTypeVisitors(schema);

		GraphQL.Builder builder = GraphQL.newGraphQL(schema);
		builder.defaultDataFetcherExceptionHandler(
				DataFetcherExceptionResolver.createExceptionHandler(this.exceptionResolvers));

		if (!this.instrumentations.isEmpty()) {
			builder = builder.instrumentation(new ChainedInstrumentation(this.instrumentations));
		}

		applyGraphQlConfigurers(builder);

		return this.graphQlSourceFactory.create(builder.build(), schema);
	}

	/**
	 * Subclasses must implement this method to provide the
	 * {@link GraphQLSchema} instance.
	 */
	protected abstract GraphQLSchema initGraphQlSchema();

	private GraphQLSchema applyTypeVisitorsToTransformSchema(GraphQLSchema schema) {
		SchemaTransformer transformer = new SchemaTransformer();
		for (GraphQLTypeVisitor visitor : this.typeVisitorsToTransformSchema) {
			schema = transformer.transform(schema, visitor);
		}
		return schema;
	}

	private GraphQLSchema applyTypeVisitors(GraphQLSchema schema) {

		GraphQLCodeRegistry.Builder outputCodeRegistry =
				GraphQLCodeRegistry.newCodeRegistry(schema.getCodeRegistry());

		Map<Class<?>, Object> vars = new HashMap<>(2);
		vars.put(GraphQLCodeRegistry.Builder.class, outputCodeRegistry);
		vars.put(TypeVisitorHelper.class, TypeVisitorHelper.create(schema));

		List<GraphQLTypeVisitor> visitorsToUse = new ArrayList<>(this.typeVisitors);
		visitorsToUse.add(ContextDataFetcherDecorator.createVisitor(this.subscriptionExceptionResolvers));

		new SchemaTraverser().depthFirstFullSchema(visitorsToUse, schema, vars);
		return schema.transformWithoutTypes((builder) -> builder.codeRegistry(outputCodeRegistry));
	}

	/**
	 * Protected method to apply the
	 * {@link #configureGraphQl(Consumer) configured graphQlConfigurer}'s.
	 * Subclasses can use this to customize {@link GraphQL.Builder} further.
	 * @param builder the builder to be customized
	 * @since 1.2.5
	 */
	protected void applyGraphQlConfigurers(GraphQL.Builder builder) {
		if (this.graphQlConfigurer != null) {
			this.graphQlConfigurer.accept(builder);
		}
	}


	/**
	 * {@link GraphQlSource} with fixed {@link GraphQL} and {@link GraphQLSchema} instances.
	 */
	private record FixedGraphQlSource(GraphQL graphQl, GraphQLSchema schema) implements GraphQlSource {
	}

}

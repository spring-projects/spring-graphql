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
package org.springframework.graphql.data.query;

import java.util.Map;
import java.util.function.Function;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.PropertyDataFetcher;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import org.springframework.lang.Nullable;

/**
 * Given a map of GraphQL type names and DataFetcher factories, find queries
 * with a matching return type and register DataFetcher's for them, unless they
 * already have registrations.
 *
 * @author Rossen Stoyanchev
 * @deprecated in favor of {@link AutoRegistrationRuntimeWiringConfigurer}
 */
@Deprecated
class AutoRegistrationTypeVisitor extends GraphQLTypeVisitorStub {

	private final Map<String, Function<Boolean, DataFetcher<?>>> dataFetcherFactories;


	/**
	 * Create an instance of the visitor.
	 * @param dataFetcherFactories map with GraphQL type names as keys and
	 * functions as values to create a DataFetcher for single or many values
	 */
	public AutoRegistrationTypeVisitor(Map<String, Function<Boolean, DataFetcher<?>>> dataFetcherFactories) {
		this.dataFetcherFactories = dataFetcherFactories;
	}


	@Override
	public TraversalControl visitGraphQLFieldDefinition(
			GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

		if (this.dataFetcherFactories.isEmpty()) {
			return TraversalControl.QUIT;
		}

		GraphQLType fieldType = fieldDefinition.getType();
		GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
		if (!parent.getName().equals("Query")) {
			return TraversalControl.ABORT;
		}

		DataFetcher<?> dataFetcher = (fieldType instanceof GraphQLList ?
				getDataFetcher(((GraphQLList) fieldType).getWrappedType(), false) :
				getDataFetcher(fieldType, true));

		if (dataFetcher != null) {
			GraphQLCodeRegistry.Builder registry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
			if (!hasDataFetcher(registry, parent, fieldDefinition)) {
				registry.dataFetcher(parent, fieldDefinition, dataFetcher);
			}
		}

		return TraversalControl.CONTINUE;
	}

	@Nullable
	private DataFetcher<?> getDataFetcher(GraphQLType type, boolean single) {
		if (type instanceof GraphQLNamedOutputType) {
			String typeName = ((GraphQLNamedOutputType) type).getName();
			Function<Boolean, DataFetcher<?>> factory = this.dataFetcherFactories.get(typeName);
			if (factory != null) {
				return factory.apply(single);
			}
		}
		return null;
	}

	private boolean hasDataFetcher(
			GraphQLCodeRegistry.Builder registry, GraphQLFieldsContainer parent,
			GraphQLFieldDefinition fieldDefinition) {

		DataFetcher<?> fetcher = registry.getDataFetcher(parent, fieldDefinition);
		return (fetcher != null && !(fetcher instanceof PropertyDataFetcher));
	}

}

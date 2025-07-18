/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.data.pagination;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import graphql.TrivialDataFetcher;
import graphql.execution.DataFetcherResult;
import graphql.relay.Connection;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.TypeVisitorHelper;
import org.springframework.util.Assert;

/**
 * {@link graphql.schema.GraphQLTypeVisitor} that looks for {@code Connection}
 * fields in the schema, and decorates their registered {@link DataFetcher} in
 * order to adapt return values to {@link Connection}.
 *
 * <p>Use {@link #create(List)} to create an instance, and then register it via
 * {@link org.springframework.graphql.execution.GraphQlSource.Builder#typeVisitors(List)}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public final class ConnectionFieldTypeVisitor extends GraphQLTypeVisitorStub {

	private static final Log logger = LogFactory.getLog(ConnectionFieldTypeVisitor.class);


	private final ConnectionAdapter adapter;


	private ConnectionFieldTypeVisitor(ConnectionAdapter adapter) {
		Assert.notNull(adapter, "ConnectionAdapter is required");
		this.adapter = adapter;
	}


	@Override
	public TraversalControl visitGraphQLFieldDefinition(
			GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

		TypeVisitorHelper visitorHelper = context.getVarFromParents(TypeVisitorHelper.class);
		GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);

		GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
		FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates(parent, fieldDefinition);
		DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(fieldCoordinates, fieldDefinition);

		if (visitorHelper != null && isUnderSubscriptionOperation(visitorHelper, context)) {
			return TraversalControl.CONTINUE;
		}

		if (isConnectionField(fieldDefinition)) {
			if (dataFetcher instanceof TrivialDataFetcher<?>) {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping connection field " +
							"'" + parent.getName() + ":" + fieldDefinition.getName() + "' " +
							"because it is mapped to trivial data fetcher: " + dataFetcher.getClass().getName());
				}
			}
			else {
				dataFetcher = new ConnectionDataFetcher(dataFetcher, this.adapter);
				codeRegistry.dataFetcher(fieldCoordinates, dataFetcher);
			}
		}

		return TraversalControl.CONTINUE;
	}

	private static boolean isUnderSubscriptionOperation(
			TypeVisitorHelper visitorHelper, TraverserContext<GraphQLSchemaElement> context) {

		return context.getBreadcrumbs().stream()
				.filter(GraphQLFieldsContainer.class::isInstance)
				.map(GraphQLFieldsContainer.class::cast)
				.anyMatch(visitorHelper::isSubscriptionType);
	}

	private static boolean isConnectionField(GraphQLFieldDefinition field) {
		GraphQLObjectType type = getAsObjectType(field);
		if (type == null || !type.getName().endsWith("Connection")) {
			return false;
		}

		GraphQLObjectType edgeType = getEdgeType(type.getField("edges"));
		if (edgeType == null || !edgeType.getName().endsWith("Edge")) {
			return false;
		}
		if (edgeType.getField("node") == null || edgeType.getField("cursor") == null) {
			return false;
		}

		GraphQLObjectType pageInfoType = getAsObjectType(type.getField("pageInfo"));
		if (pageInfoType == null || !pageInfoType.getName().equals("PageInfo")) {
			return false;
		}
		if (pageInfoType.getField("hasPreviousPage") == null || pageInfoType.getField("hasNextPage") == null ||
				pageInfoType.getField("startCursor") == null || pageInfoType.getField("endCursor") == null) {
			return false;
		}

		return true;
	}

	private static @Nullable GraphQLObjectType getAsObjectType(@Nullable GraphQLFieldDefinition field) {
		return (getType(field) instanceof GraphQLObjectType type) ? type : null;
	}

	private static @Nullable GraphQLObjectType getEdgeType(@Nullable GraphQLFieldDefinition field) {
		if (getType(field) instanceof GraphQLList listType) {
			if (unwrapNonNullType(listType.getWrappedType()) instanceof GraphQLObjectType type) {
				return type;
			}
		}
		return null;
	}

	private static @Nullable GraphQLType getType(@Nullable GraphQLFieldDefinition field) {
		if (field == null) {
			return null;
		}
		return unwrapNonNullType(field.getType());
	}

	private static @Nullable GraphQLType unwrapNonNullType(@Nullable GraphQLType type) {
		if (type == null) {
			return null;
		}
		return (type instanceof GraphQLNonNull nonNullType) ? nonNullType.getWrappedType() : type;
	}


	/**
	 * Create a {@code ConnectionTypeVisitor} instance that delegates to the
	 * given adapters to adapt return values to {@link Connection}.
	 * @param adapters the adapters to use
	 * @return the type visitor
	 */
	public static ConnectionFieldTypeVisitor create(List<ConnectionAdapter> adapters) {
		Assert.notEmpty(adapters, "Expected at least one ConnectionAdapter");
		return new ConnectionFieldTypeVisitor(ConnectionAdapter.from(adapters));
	}


	/**
	 * {@code DataFetcher} decorator that adapts return values with an adapter.
	 * @param delegate the datafetcher delegate
	 * @param adapter the connection adapter to use
	 */
	record ConnectionDataFetcher(DataFetcher<?> delegate, ConnectionAdapter adapter) implements DataFetcher<Object> {

		private static final Connection<?> EMPTY_CONNECTION =
				new DefaultConnection<>(Collections.emptyList(), new DefaultPageInfo(null, null, false, false));


		ConnectionDataFetcher {
			Assert.notNull(delegate, "DataFetcher delegate is required");
			Assert.notNull(adapter, "ConnectionAdapter is required");
		}


		@Override
		public Object get(DataFetchingEnvironment environment) throws Exception {
			Object result = this.delegate.get(environment);
			if (result instanceof Mono<?> mono) {
				return mono.map(this::adaptDataFetcherResult);
			}
			else if (result instanceof CompletionStage<?> stage) {
				return stage.thenApply(this::adaptDataFetcherResult);
			}
			else {
				return adaptDataFetcherResult(result);
			}
		}

		private Object adaptDataFetcherResult(@Nullable Object value) {
			if (value instanceof DataFetcherResult<?> dataFetcherResult) {
				Object adapted = adaptDataContainer(dataFetcherResult.getData());
				return DataFetcherResult.newResult()
						.data(adapted)
						.errors(dataFetcherResult.getErrors())
						.localContext(dataFetcherResult.getLocalContext()).build();
			}
			else {
				return adaptDataContainer(value);
			}
		}

		private <T> Object adaptDataContainer(@Nullable Object container) {
			if (container == null) {
				return EMPTY_CONNECTION;
			}

			if (container instanceof Connection<?>) {
				return container;
			}

			if (!this.adapter.supports(container.getClass())) {
				if (container.getClass().getName().endsWith("Connection")) {
					return container;
				}
				throw new IllegalStateException(
						"No ConnectionAdapter for: " + container.getClass().getName());
			}

			Collection<T> nodes = this.adapter.getContent(container);
			if (nodes.isEmpty()) {
				return EMPTY_CONNECTION;
			}

			int index = 0;
			List<Edge<T>> edges = new ArrayList<>(nodes.size());
			for (T node : nodes) {
				String cursor = this.adapter.cursorAt(container, index++);
				edges.add(new DefaultEdge<>(node, new DefaultConnectionCursor(cursor)));
			}

			DefaultPageInfo pageInfo = new DefaultPageInfo(
					edges.get(0).getCursor(), edges.get(edges.size() - 1).getCursor(),
					this.adapter.hasPrevious(container), this.adapter.hasNext(container));

			return new DefaultConnection<>(edges, pageInfo);
		}

	}

}

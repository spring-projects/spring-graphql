/*
 * Copyright 2020-2023 the original author or authors.
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

import graphql.relay.Connection;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import reactor.core.publisher.Mono;

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
 * @since 1.2
 */
public final class ConnectionFieldTypeVisitor extends GraphQLTypeVisitorStub {

	private final ConnectionAdapter adapter;


	private ConnectionFieldTypeVisitor(ConnectionAdapter adapter) {
		Assert.notNull(adapter, "ConnectionAdapter is required");
		this.adapter = adapter;
	}


	@Override
	public TraversalControl visitGraphQLFieldDefinition(
			GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

		GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
		GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
		DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parent, fieldDefinition);

		if (parent.getName().equalsIgnoreCase("mutation") || parent.getName().equalsIgnoreCase("subscription")) {
			return TraversalControl.ABORT;
		}

		if (isConnectionField(fieldDefinition)) {
			codeRegistry.dataFetcher(parent, fieldDefinition, new ConnectionDataFetcher(dataFetcher, adapter));
		}

		return TraversalControl.CONTINUE;
	}

	private static boolean isConnectionField(GraphQLFieldDefinition fieldDefinition) {
		GraphQLType returnType = fieldDefinition.getType();
		if (returnType instanceof GraphQLNonNull nonNullType) {
			returnType = nonNullType.getWrappedType();
		}
		return (returnType instanceof GraphQLObjectType objectType &&
				objectType.getName().endsWith("Connection") &&
				objectType.getField("pageInfo") != null);
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
	 */
	private record ConnectionDataFetcher(DataFetcher<?> delegate, ConnectionAdapter adapter) implements DataFetcher<Object> {

		private final static Connection<?> EMPTY_CONNECTION =
				new DefaultConnection<>(Collections.emptyList(), new DefaultPageInfo(null, null, false, false));


		private ConnectionDataFetcher {
			Assert.notNull(delegate, "DataFetcher delegate is required");
			Assert.notNull(adapter, "ConnectionAdapter is required");
		}


		@Override
		public Object get(DataFetchingEnvironment environment) throws Exception {
			Object result = this.delegate.get(environment);
			if (result instanceof Mono<?> mono) {
				return mono.map(this::adapt);
			}
			else if (result instanceof CompletionStage<?> stage) {
				return stage.thenApply(this::adapt);
			}
			else {
				return adapt(result);
			}
		}

		@SuppressWarnings("unchecked")
		private <T> Connection<T> adapt(Object container) {
			if (container instanceof Connection<?> connection) {
				return (Connection<T>) connection;
			}

			Collection<T> nodes = this.adapter.getContent(container);
			if (nodes.isEmpty()) {
				return (Connection<T>) EMPTY_CONNECTION;
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

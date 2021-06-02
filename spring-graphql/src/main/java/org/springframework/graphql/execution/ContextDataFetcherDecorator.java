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

import graphql.ExecutionInput;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.util.Assert;

/**
 * Wrap a {@link DataFetcher} to enable the following:
 * <ul>
 * <li>Support {@link Mono} return value.
 * <li>Support {@link Flux} return value as a shortcut to {@link Flux#collectList()}.
 * <li>Re-establish Reactor Context passed via {@link ExecutionInput}.
 * <li>Re-establish ThreadLocal context passed via {@link ExecutionInput}.
 * </ul>
 *
 * @author Rossen Stoyanchev
 */
final class ContextDataFetcherDecorator implements DataFetcher<Object> {

	private final DataFetcher<?> delegate;

	private final boolean subscription;

	private ContextDataFetcherDecorator(DataFetcher<?> delegate, boolean subscription) {
		Assert.notNull(delegate, "'delegate' DataFetcher is required");
		this.delegate = delegate;
		this.subscription = subscription;
	}

	@Override
	public Object get(DataFetchingEnvironment environment) throws Exception {
		ContextView contextView = ContextManager.getReactorContext(environment);

		Object value;
		try {
			ContextManager.restoreThreadLocalValues(contextView);
			value = this.delegate.get(environment);
		}
		finally {
			ContextManager.resetThreadLocalValues(contextView);
		}

		if (this.subscription) {
			return (!contextView.isEmpty() ? Flux.from((Publisher<?>) value).contextWrite(contextView) : value);
		}

		if (value instanceof Flux) {
			value = ((Flux<?>) value).collectList();
		}

		if (value instanceof Mono) {
			Mono<?> valueMono = (Mono<?>) value;
			if (!contextView.isEmpty()) {
				valueMono = valueMono.contextWrite(contextView);
			}
			value = valueMono.toFuture();
		}

		return value;
	}

	/**
	 * {@link GraphQLTypeVisitor} that wraps non-GraphQL data fetchers and adapts them if
	 * they return {@link Flux} or {@link Mono}.
	 */
	static GraphQLTypeVisitor TYPE_VISITOR = new GraphQLTypeVisitorStub() {

		@Override
		public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition,
				TraverserContext<GraphQLSchemaElement> context) {

			GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
			GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
			DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parent, fieldDefinition);

			if (dataFetcher.getClass().getPackage().getName().startsWith("graphql.")) {
				return TraversalControl.CONTINUE;
			}

			boolean handlesSubscription = parent.getName().equals("Subscription");
			dataFetcher = new ContextDataFetcherDecorator(dataFetcher, handlesSubscription);
			codeRegistry.dataFetcher(parent, fieldDefinition, dataFetcher);
			return TraversalControl.CONTINUE;
		}
	};

}

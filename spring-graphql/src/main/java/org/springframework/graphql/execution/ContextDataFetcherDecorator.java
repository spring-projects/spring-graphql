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

import java.util.List;

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
 * <li>Resolve exceptions from a GraphQL subscription {@link Publisher}.
 * </ul>
 *
 * @author Rossen Stoyanchev
 */
final class ContextDataFetcherDecorator implements DataFetcher<Object> {

	private final DataFetcher<?> delegate;

	private final boolean subscription;

	private final SubscriptionExceptionResolver subscriptionExceptionResolver;

	private ContextDataFetcherDecorator(
			DataFetcher<?> delegate, boolean subscription,
			SubscriptionExceptionResolver subscriptionExceptionResolver) {

		Assert.notNull(delegate, "'delegate' DataFetcher is required");
		Assert.notNull(subscriptionExceptionResolver, "'subscriptionExceptionResolver' is required");
		this.delegate = delegate;
		this.subscription = subscription;
		this.subscriptionExceptionResolver = subscriptionExceptionResolver;
	}

	@Override
	public Object get(DataFetchingEnvironment environment) throws Exception {

		Object value = ReactorContextManager.invokeCallable(() ->
				this.delegate.get(environment), environment.getGraphQlContext());

		ContextView contextView = ReactorContextManager.getReactorContext(environment.getGraphQlContext());

		if (this.subscription) {
			Assert.state(value instanceof Publisher, "Expected Publisher for a subscription");
			Flux<?> flux = Flux.from((Publisher<?>) value).onErrorResume(exception ->
					this.subscriptionExceptionResolver.resolveException(exception)
							.flatMap(errors -> Mono.error(new SubscriptionPublisherException(errors, exception))));
			return (!contextView.isEmpty() ? flux.contextWrite(contextView) : flux);
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
	 * Static factory method to create {@link GraphQLTypeVisitor} that wraps
	 * data fetchers with the {@link ContextDataFetcherDecorator}.
	 */
	static GraphQLTypeVisitor createVisitor(List<SubscriptionExceptionResolver> resolvers) {

		SubscriptionExceptionResolver compositeResolver = new CompositeSubscriptionExceptionResolver(resolvers);

		return new GraphQLTypeVisitorStub() {
			@Override
			public TraversalControl visitGraphQLFieldDefinition(
					GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

				GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
				GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
				DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parent, fieldDefinition);

				if (skipDataFetcher(dataFetcher)) {
					return TraversalControl.CONTINUE;
				}

				boolean handlesSubscription = parent.getName().equals("Subscription");
				dataFetcher = new ContextDataFetcherDecorator(dataFetcher, handlesSubscription, compositeResolver);
				codeRegistry.dataFetcher(parent, fieldDefinition, dataFetcher);
				return TraversalControl.CONTINUE;
			}

			private boolean skipDataFetcher(DataFetcher<?> dataFetcher) {
				Class<?> type = dataFetcher.getClass();
				if (type.getPackage().getName().startsWith("graphql.")) {
					return !type.getSimpleName().startsWith("DataFetcherFactories");
				}
				return false;
			}
		};
	}

}

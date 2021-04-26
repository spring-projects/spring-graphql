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
package org.springframework.graphql.data;

import java.lang.reflect.Method;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
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
import org.springframework.util.ClassUtils;

/**
 * Adapter to wrap a registered {@link DataFetcher} and enable it to return
 * {@link Flux} or {@link Mono}, also adding Reactor Context passed through
 * the {@link ExecutionInput} via {@link #addReactorContext(ExecutionInput, ContextView)}.
 * Use {@link #TYPE_VISITOR} to transform the
 * {@link graphql.schema.GraphQLSchema} and apply the adapter.
 */
public class ReactorDataFetcherAdapter implements DataFetcher<Object> {

	private static final String REACTOR_CONTEXT_KEY =
			ReactorDataFetcherAdapter.class.getName() + ".REACTOR_CONTEXT";


	private final DataFetcher<?> delegate;

	private final boolean subscription;


	private ReactorDataFetcherAdapter(DataFetcher<?> delegate, boolean subscription) {
		Assert.notNull(delegate, "'delegate' DataFetcher is required");
		this.delegate = delegate;
		this.subscription = subscription;
	}


	@Override
	public Object get(DataFetchingEnvironment environment) throws Exception {
		Object value = this.delegate.get(environment);

		if (this.subscription) {
			ContextView context = getReactorContext(environment);
			return (context != null ? Flux.from((Publisher<?>) value).contextWrite(context) : value);
		}

		if (value instanceof Flux) {
			value = ((Flux<?>) value).collectList();
		}

		if (value instanceof Mono) {
			Mono<?> valueMono = (Mono<?>) value;
			ContextView reactorContext = getReactorContext(environment);
			if (reactorContext != null) {
				valueMono = valueMono.contextWrite(reactorContext);
			}
			value = valueMono.toFuture();
		}

		return value;
	}

	private ContextView getReactorContext(DataFetchingEnvironment environment) {
		GraphQLContext graphQLContext = environment.getContext();
		return graphQLContext.get(REACTOR_CONTEXT_KEY);
	}

	/**
	 * Insert the given Reactor Context into the {@link ExecutionInput} context
	 * for later retrieval from the {@link DataFetchingEnvironment}.
	 */
	public static void addReactorContext(ExecutionInput executionInput, ContextView reactorContext) {
		GraphQLContext graphQLContext = (GraphQLContext) executionInput.getContext();
		graphQLContext.put(REACTOR_CONTEXT_KEY, reactorContext);
	}


	/**
	 * {@link GraphQLTypeVisitor} that wraps non-GraphQL data fetchers and
	 * adapts them if they return {@link Flux} or {@link Mono}.
	 */
	public static GraphQLTypeVisitor TYPE_VISITOR = new GraphQLTypeVisitorStub() {

		@Override
		public TraversalControl visitGraphQLFieldDefinition(
				GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

			GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
			GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
			DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parent, fieldDefinition);

			if (dataFetcher.getClass().getPackage().getName().startsWith("graphql.")) {
				return TraversalControl.CONTINUE;
			}

			Method method = ClassUtils.getMethod(dataFetcher.getClass(), "get", DataFetchingEnvironment.class);
			method = ClassUtils.getMostSpecificMethod(method, dataFetcher.getClass());
			Class<?> returnType = method.getReturnType();
			System.out.println(returnType.getName());

			dataFetcher = new ReactorDataFetcherAdapter(dataFetcher, parent.getName().equals("Subscription"));
			codeRegistry.dataFetcher(parent, fieldDefinition, dataFetcher);
			return TraversalControl.CONTINUE;
		}
	};

}

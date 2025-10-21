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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.TrivialDataFetcher;
import graphql.execution.AbortExecutionException;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

/**
 * Wrap a {@link DataFetcher} to enable the following:
 * <ul>
 * <li>Support {@link Mono} return value.
 * <li>Support {@link Flux} return value as a shortcut to {@link Flux#collectList()}.
 * <li>Re-establish Reactor Context passed via {@link ExecutionInput}.
 * <li>Re-establish ThreadLocal context passed via {@link ExecutionInput}.
 * <li>Resolve exceptions from a GraphQL subscription {@link Publisher}.
 * <li>Propagate the cancellation signal to {@code DataFetcher} from the transport layer.
 * </ul>
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
class ContextDataFetcherDecorator implements DataFetcher<Object> {

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
	@SuppressWarnings("NullAway")
	public @Nullable Object get(DataFetchingEnvironment env) throws Exception {

		GraphQLContext graphQlContext = env.getGraphQlContext();
		ContextSnapshotFactory snapshotFactory = ContextPropagationHelper.getInstance(graphQlContext);
		ContextSnapshot snapshot = (env.getLocalContext() instanceof GraphQLContext localContext) ?
				snapshotFactory.captureFrom(graphQlContext, localContext) :
				snapshotFactory.captureFrom(graphQlContext);

		Object value = snapshot.wrap(() -> this.delegate.get(env)).call();

		if (value instanceof DataFetcherResult<?> dataFetcherResult) {
			value = dataFetcherResult.map((data) -> updateValue(data, snapshot, graphQlContext));
		}
		else {
			value = updateValue(value, snapshot, graphQlContext);
		}

		return value;
	}

	@SuppressWarnings("ReactiveStreamsUnusedPublisher")
	private @Nullable Object updateValue(
			@Nullable Object value, ContextSnapshot snapshot, GraphQLContext graphQlContext) {

		if (value == null) {
			return null;
		}
		if (ContextPropagationHelper.isCancelled(graphQlContext)) {
			return DataFetcherResult.newResult()
					.error(new AbortExecutionException("GraphQL request has been cancelled by the client."))
					.build();
		}

		if (this.subscription) {
			Flux<?> subscriptionResult = ReactiveAdapterRegistryHelper.toSubscriptionFlux(value)
					.onErrorResume((exception) -> {
						// Already handled, e.g. controller methods?
						if (exception instanceof SubscriptionPublisherException) {
							return Mono.error(exception);
						}
						return this.subscriptionExceptionResolver.resolveException(exception)
								.flatMap((errors) -> Mono.error(new SubscriptionPublisherException(errors, exception)));
					});
			return ContextPropagationHelper.bindCancelFrom(subscriptionResult, graphQlContext)
					.contextWrite(snapshot::updateContext);
		}

		value = ReactiveAdapterRegistryHelper.toMonoIfReactive(value);

		if (value instanceof Mono<?> mono) {
			value = mono.contextWrite(snapshot::updateContext).toFuture();
		}

		return value;
	}


	/**
	 * Static factory method to create {@link GraphQLTypeVisitor} that wraps
	 * data fetchers with the {@link ContextDataFetcherDecorator}.
	 */
	static GraphQLTypeVisitor createVisitor(List<SubscriptionExceptionResolver> resolvers) {
		return new ContextTypeVisitor(resolvers);
	}


	/**
	 * Type visitor to apply {@link ContextDataFetcherDecorator}.
	 */
	private static final class ContextTypeVisitor extends GraphQLTypeVisitorStub {

		private final SubscriptionExceptionResolver exceptionResolver;

		private ContextTypeVisitor(List<SubscriptionExceptionResolver> resolvers) {
			this.exceptionResolver = new CompositeSubscriptionExceptionResolver(resolvers);
		}

		@Override
		public TraversalControl visitGraphQLFieldDefinition(
				GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

			TypeVisitorHelper visitorHelper = context.getVarFromParents(TypeVisitorHelper.class);
			GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);

			GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
			FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates(parent, fieldDefinition);
			DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(fieldCoordinates, fieldDefinition);

			if (applyDecorator(dataFetcher)) {
				boolean subscriptionType = visitorHelper.isSubscriptionType(parent);
				dataFetcher = decorate(dataFetcher, subscriptionType, this.exceptionResolver);
				codeRegistry.dataFetcher(fieldCoordinates, dataFetcher);
			}

			return TraversalControl.CONTINUE;
		}

		private boolean applyDecorator(DataFetcher<?> dataFetcher) {
			if (dataFetcher instanceof TrivialDataFetcher) {
				return false;
			}
			Class<?> type = dataFetcher.getClass();
			String packageName = type.getPackage().getName();
			if (packageName.startsWith("graphql.")) {
				return (type.getSimpleName().startsWith("DataFetcherFactories") ||
						packageName.startsWith("graphql.validation"));
			}
			return true;
		}

		private static ContextDataFetcherDecorator decorate(
				DataFetcher<?> dataFetcher, boolean subscriptionType, SubscriptionExceptionResolver exceptionResolver) {

			return ((dataFetcher instanceof SelfDescribingDataFetcher<?> sddf) ?
					new SelfDescribingContextDataFetcherDecorator(sddf, subscriptionType, exceptionResolver) :
					new ContextDataFetcherDecorator(dataFetcher, subscriptionType, exceptionResolver));
		}
	}


	private static final class SelfDescribingContextDataFetcherDecorator extends ContextDataFetcherDecorator
			implements SelfDescribingDataFetcher<Object> {

		private final SelfDescribingDataFetcher<?> delegate;

		private SelfDescribingContextDataFetcherDecorator(
				SelfDescribingDataFetcher<?> delegate, boolean subscriptionType,
				SubscriptionExceptionResolver exceptionResolver) {

			super(delegate, subscriptionType, exceptionResolver);
			this.delegate = delegate;
		}

		@Override
		public String getDescription() {
			return this.delegate.getDescription();
		}

		@Override
		public ResolvableType getReturnType() {
			return this.delegate.getReturnType();
		}

		@Override
		public @Nullable Method asMethod() {
			return this.delegate.asMethod();
		}

		@Override
		public Map<String, ResolvableType> getArguments() {
			return this.delegate.getArguments();
		}

		@Override
		public boolean usesDataLoader() {
			return this.delegate.usesDataLoader();
		}
	}

}

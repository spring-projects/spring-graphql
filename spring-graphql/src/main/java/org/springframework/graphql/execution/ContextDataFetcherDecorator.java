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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.TrivialDataFetcher;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
 * </ul>
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
class ContextDataFetcherDecorator implements DataFetcher<Object> {

	private static final Log logger = LogFactory.getLog(ContextDataFetcherDecorator.class);

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
		ContextSnapshot snapshot = (env.getLocalContext() instanceof GraphQLContext localContext &&
				localContext != graphQlContext) ?
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
			return subscriptionResult.contextWrite(snapshot::updateContext);
		}

		if (value instanceof CompletionStage<?> stage) {
			// Wrap the CompletionStage directly instead of converting it through
			// Mono (toMonoIfReactive + contextWrite + toFuture). The Reactor path
			// allocated several objects per field and restored ThreadLocals only when
			// Reactor's automatic context propagation was enabled; the direct wrapper
			// installs the snapshot scope explicitly, independent of the Hooks state.
			return wrapCompletionStage(stage, snapshot);
		}

		value = ReactiveAdapterRegistryHelper.toMonoIfReactive(value);

		if (value instanceof Mono<?> mono) {
			value = mono.contextWrite(snapshot::updateContext).toFuture();
		}

		return value;
	}

	@SuppressWarnings({"unchecked", "try"})
	private static CompletableFuture<@Nullable Object> wrapCompletionStage(
			CompletionStage<?> stage, ContextSnapshot snapshot) {

		if (stage instanceof CompletableFuture<?> future && future.isDone()
				&& !future.isCompletedExceptionally()) {
			// Dependents of an already-completed future run inline on the current thread,
			// which holds its own context; no propagation wrapper is needed. Only normally
			// completed futures take this path: a failed one must still be unwrapped below.
			return (CompletableFuture<@Nullable Object>) future;
		}

		CompletableFuture<@Nullable Object> result = new CompletableFuture<>();

		// Mono.fromCompletionStage propagated cancellation of the returned future back
		// to the source; preserve that by cancelling the source when the result is cancelled.
		result.whenComplete((value, throwable) -> {
			if (result.isCancelled() && stage instanceof Future<?> future) {
				future.cancel(true);
			}
		});

		stage.whenComplete((value, throwable) -> {
			if (throwable instanceof CompletionException exception && exception.getCause() != null) {
				// MonoCompletionStage unwraps CompletionException before onError; match that.
				throwable = exception.getCause();
			}
			try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
				if (throwable != null) {
					result.completeExceptionally(throwable);
				}
				else {
					result.complete(value);
				}
			}
			catch (Throwable ex) {
				if (throwable != null) {
					// Keep the original failure primary. Note addSuppressed mutates the
					// stage's throwable: a shared failed future accumulates one suppressed
					// entry per scope failure. Accepted, since scope failure here requires
					// a failing ThreadLocalAccessor, which is a bug of its own.
					throwable.addSuppressed(ex);
					result.completeExceptionally(throwable);
				}
				else if (!result.completeExceptionally(ex)) {
					logger.warn("ContextSnapshot scope failure after the future completed", ex);
				}
			}
		});
		return result;
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

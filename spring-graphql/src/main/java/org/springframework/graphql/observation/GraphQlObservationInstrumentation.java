/*
 * Copyright 2020-2025 the original author or authors.
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

package org.springframework.graphql.observation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.instrumentation.DataLoaderInstrumentation;
import org.dataloader.instrumentation.DataLoaderInstrumentationContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.springframework.graphql.execution.SelfDescribingDataFetcher;

/**
 * {@link graphql.execution.instrumentation.Instrumentation} that creates
 * {@link Observation observations} for GraphQL requests and data fetcher operations.
 * <p>GraphQL request instrumentation measures the execution time of requests
 * and collects information from the {@link ExecutionRequestObservationContext}.
 * A request can perform many data fetching operations.
 * The configured {@link ExecutionRequestObservationConvention} will be used,
 * or the {@link DefaultExecutionRequestObservationConvention} if none was provided.
 * <p>GraphQL data fetcher instrumentation measures the execution time of
 * a data fetching operation in the context of the current request.
 * Information is collected from the {@link DataFetcherObservationContext}.
 * The configured {@link DataFetcherObservationConvention} will be used,
 * or the {@link DefaultDataFetcherObservationConvention} if none was provided.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class GraphQlObservationInstrumentation extends SimplePerformantInstrumentation {

	private static final ExecutionRequestObservationConvention DEFAULT_REQUEST_CONVENTION =
			new DefaultExecutionRequestObservationConvention();

	private static final DataFetcherObservationConvention DEFAULT_DATA_FETCHER_CONVENTION =
			new DefaultDataFetcherObservationConvention();

	private static final DefaultDataLoaderObservationConvention DEFAULT_DATA_LOADER_CONVENTION =
			new DefaultDataLoaderObservationConvention();

	private final ObservationRegistry observationRegistry;

	private final @Nullable ExecutionRequestObservationConvention requestObservationConvention;

	private final @Nullable DataFetcherObservationConvention dataFetcherObservationConvention;

	private final @Nullable DataLoaderObservationConvention dataLoaderObservationConvention;

	/**
	 * Create an {@code GraphQlObservationInstrumentation} that records observations
	 * against the given {@link ObservationRegistry}. The default observation
	 * conventions will be used.
	 * @param observationRegistry the registry to use for recording observations
	 */
	public GraphQlObservationInstrumentation(ObservationRegistry observationRegistry) {
		this(observationRegistry, null, null, null);
	}

	/**
	 * Create an {@code GraphQlObservationInstrumentation} that records observations
	 * against the given {@link ObservationRegistry} with a custom convention.
	 * @param observationRegistry the registry to use for recording observations
	 * @param requestObservationConvention the convention to use for request observations
	 * @param dateFetcherObservationConvention the convention to use for data fetcher observations
	 * @deprecated since 1.4.0 in favor of {@link #GraphQlObservationInstrumentation(ObservationRegistry,
	 * ExecutionRequestObservationConvention, DataFetcherObservationConvention, DataLoaderObservationConvention)}
	 */
	@Deprecated(since = "1.4.0", forRemoval = true)
	public GraphQlObservationInstrumentation(ObservationRegistry observationRegistry,
				@Nullable ExecutionRequestObservationConvention requestObservationConvention,
				@Nullable DataFetcherObservationConvention dateFetcherObservationConvention) {
		this(observationRegistry, requestObservationConvention, dateFetcherObservationConvention, null);
	}

	/**
	 * Create an {@code GraphQlObservationInstrumentation} that records observations
	 * against the given {@link ObservationRegistry} with a custom convention.
	 * @param observationRegistry the registry to use for recording observations
	 * @param requestObservationConvention the convention to use for request observations
	 * @param dateFetcherObservationConvention the convention to use for data fetcher observations
	 * @param dataLoaderObservationConvention the convention to use for data loader observations
	 * @since 1.4.0
	 */
	public GraphQlObservationInstrumentation(ObservationRegistry observationRegistry,
											@Nullable ExecutionRequestObservationConvention requestObservationConvention,
											@Nullable DataFetcherObservationConvention dateFetcherObservationConvention,
											@Nullable DataLoaderObservationConvention dataLoaderObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.requestObservationConvention = requestObservationConvention;
		this.dataFetcherObservationConvention = dateFetcherObservationConvention;
		this.dataLoaderObservationConvention = dataLoaderObservationConvention;
	}

	@Override
	public @NonNull ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
		return executionInput.transform((builder) -> {
			DataLoaderRegistry dataLoaderRegistry = DataLoaderRegistry.newRegistry()
					.registerAll(executionInput.getDataLoaderRegistry())
					.instrumentation(new ObservationDataLoaderInstrumentation())
					.build();
			builder.dataLoaderRegistry(dataLoaderRegistry);
		});
	}

	@Override
	public CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
		return CompletableFuture.completedFuture(RequestObservationInstrumentationState.INSTANCE);
	}

	@Override
	public @Nullable InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters,
			InstrumentationState state) {
		if (state == RequestObservationInstrumentationState.INSTANCE) {
			ExecutionRequestObservationContext observationContext = new ExecutionRequestObservationContext(parameters.getExecutionInput());
			Observation requestObservation = GraphQlObservationDocumentation.EXECUTION_REQUEST.observation(this.requestObservationConvention,
					DEFAULT_REQUEST_CONVENTION, () -> observationContext, this.observationRegistry);
			setCurrentObservation(requestObservation, parameters.getGraphQLContext());
			requestObservation.start();
			return new SimpleInstrumentationContext<>() {
				@Override
				public void onCompleted(ExecutionResult result, @Nullable Throwable exc) {
					observationContext.setExecutionResult(result);
					result.getErrors().forEach((graphQLError) -> {
						Observation.Event event = Observation.Event.of(graphQLError.getErrorType().toString(), graphQLError.getMessage());
						requestObservation.event(event);
					});
					if (exc != null) {
						requestObservation.error(exc);
					}
					requestObservation.stop();
				}
			};
		}
		return super.beginExecution(parameters, state);
	}

	private static void setCurrentObservation(Observation currentObservation, GraphQLContext graphQlContext) {
		Observation parentObservation = graphQlContext.get(ObservationThreadLocalAccessor.KEY);
		currentObservation.parentObservation(parentObservation);
		graphQlContext.put(ObservationThreadLocalAccessor.KEY, currentObservation);
	}

	@Override
	public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
			InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
		if (!parameters.isTrivialDataFetcher()
				&& state == RequestObservationInstrumentationState.INSTANCE) {
			// skip batch loading operations, already instrumented at the dataloader level
			if (dataFetcher instanceof SelfDescribingDataFetcher<?> selfDescribingDataFetcher
					&& selfDescribingDataFetcher.usesDataLoader()) {
				return dataFetcher;
			}
			return (environment) -> {
				DataFetcherObservationContext observationContext = new DataFetcherObservationContext(environment);
				Observation dataFetcherObservation = GraphQlObservationDocumentation.DATA_FETCHER.observation(this.dataFetcherObservationConvention,
						DEFAULT_DATA_FETCHER_CONVENTION, () -> observationContext, this.observationRegistry);
				dataFetcherObservation.parentObservation(getCurrentObservation(environment));
				dataFetcherObservation.start();

				DataFetchingEnvironment dataFetchingEnvironment = wrapDataFetchingEnvironment(environment, dataFetcherObservation);
				try {
					Object value = dataFetcher.get(dataFetchingEnvironment);
					if (value instanceof CompletionStage<?> completion) {
						return completion.handle((result, error) -> {
							observationContext.setValue(result);
							if (error != null) {
								if (error instanceof CompletionException completionException) {
									dataFetcherObservation.error((error.getCause() != null) ? error.getCause() : error);
									dataFetcherObservation.stop();
									throw completionException;
								}
								else {
									dataFetcherObservation.error(error);
									dataFetcherObservation.stop();
									throw new CompletionException(error);
								}
							}
							dataFetcherObservation.stop();
							return result;
						});
					}
					else {
						observationContext.setValue(value);
						dataFetcherObservation.stop();
						return value;
					}
				}
				catch (Throwable throwable) {
					dataFetcherObservation.error(throwable);
					dataFetcherObservation.stop();
					throw throwable;
				}
			};
		}
		return dataFetcher;
	}

	private static @Nullable Observation getCurrentObservation(DataFetchingEnvironment environment) {
		Observation currentObservation = null;
		if (environment.getLocalContext() instanceof GraphQLContext localContext) {
			currentObservation = localContext.get(ObservationThreadLocalAccessor.KEY);
		}
		if (currentObservation == null) {
			currentObservation = environment.getGraphQlContext().get(ObservationThreadLocalAccessor.KEY);
		}
		return currentObservation;
	}

	private static DataFetchingEnvironment wrapDataFetchingEnvironment(DataFetchingEnvironment environment, Observation dataFetcherObservation) {
		if (environment.getLocalContext() == null || environment.getLocalContext() instanceof GraphQLContext) {
			GraphQLContext.Builder localContextBuilder = GraphQLContext.newContext();
			if (environment.getLocalContext() instanceof GraphQLContext localContext) {
				localContextBuilder.of(localContext);
			}
			localContextBuilder.of(ObservationThreadLocalAccessor.KEY, dataFetcherObservation);
			return DataFetchingEnvironmentImpl
					.newDataFetchingEnvironment(environment)
					.localContext(localContextBuilder.build())
					.build();
		}
		// do not wrap environment, there is an existing custom context
		return environment;
	}


	static class RequestObservationInstrumentationState implements InstrumentationState {

		static final RequestObservationInstrumentationState INSTANCE = new RequestObservationInstrumentationState();

	}

	class ObservationDataLoaderInstrumentation implements DataLoaderInstrumentation {

		@Override
		public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {

			Observation observation = GraphQlObservationDocumentation.DATA_LOADER
					.observation(GraphQlObservationInstrumentation.this.dataLoaderObservationConvention,
							DEFAULT_DATA_LOADER_CONVENTION,
							() -> new DataLoaderObservationContext(dataLoader, keys, environment),
							GraphQlObservationInstrumentation.this.observationRegistry);
			if (environment.getContext() instanceof GraphQLContext graphQLContext) {
				Observation parentObservation = graphQLContext.get(ObservationThreadLocalAccessor.KEY);
				observation.parentObservation(parentObservation);
			}
			return new DataLoaderInstrumentationContext<List<?>>() {
				@Override
				public void onDispatched() {
					observation.start();
				}

				@Override
				public void onCompleted(List<?> result, @Nullable Throwable t) {
					DataLoaderObservationContext context = (DataLoaderObservationContext) observation.getContext();
					context.setResult(result);
					if (t != null) {
						observation.error(t);
					}
					observation.stop();
				}
			};
		}
	}

}

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

package org.springframework.graphql.observation;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

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

	private static final Log logger = LogFactory.getLog(GraphQlObservationInstrumentation.class);

	private static final ExecutionRequestObservationConvention DEFAULT_REQUEST_CONVENTION =
			new DefaultExecutionRequestObservationConvention();

	private static final DataFetcherObservationConvention DEFAULT_DATA_FETCHER_CONVENTION =
			new DefaultDataFetcherObservationConvention();

	private final ObservationRegistry observationRegistry;

	private final ExecutionRequestObservationConvention requestObservationConvention;

	private final DataFetcherObservationConvention dataFetcherObservationConvention;

	/**
	 * Create an {@code GraphQlObservationInstrumentation} that records observations
	 * against the given {@link ObservationRegistry}. The default observation
	 * conventions will be used.
	 * @param observationRegistry the registry to use for recording observations
	 */
	public GraphQlObservationInstrumentation(ObservationRegistry observationRegistry) {
		this.observationRegistry = observationRegistry;
		this.requestObservationConvention = new DefaultExecutionRequestObservationConvention();
		this.dataFetcherObservationConvention = new DefaultDataFetcherObservationConvention();
	}

	/**
	 * Create an {@code GraphQlObservationInstrumentation} that records observations
	 * against the given {@link ObservationRegistry} with a custom convention.
	 * @param observationRegistry the registry to use for recording observations
	 * @param requestObservationConvention the convention to use for request observations
	 * @param dateFetcherObservationConvention the convention to use for data fetcher observations
	 */
	public GraphQlObservationInstrumentation(ObservationRegistry observationRegistry,
			ExecutionRequestObservationConvention requestObservationConvention,
			DataFetcherObservationConvention dateFetcherObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.requestObservationConvention = requestObservationConvention;
		this.dataFetcherObservationConvention = dateFetcherObservationConvention;
	}

	@Override
	public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
		return RequestObservationInstrumentationState.INSTANCE;
	}

	@Override
	public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters,
			InstrumentationState state) {
		if (state instanceof RequestObservationInstrumentationState instrumentationState) {
			ExecutionRequestObservationContext observationContext = new ExecutionRequestObservationContext(parameters.getExecutionInput());
			Observation requestObservation = GraphQlObservationDocumentation.EXECUTION_REQUEST.observation(this.requestObservationConvention,
					DEFAULT_REQUEST_CONVENTION, () -> observationContext, this.observationRegistry);
			requestObservation.parentObservation(getCurrentObservation(parameters.getGraphQLContext()));
			GraphQLContext graphQLContext = parameters.getGraphQLContext();
			graphQLContext.put(ObservationThreadLocalAccessor.KEY, requestObservation);
			requestObservation.start();
			return new SimpleInstrumentationContext<>() {
				@Override
				public void onCompleted(ExecutionResult result, Throwable exc) {
					observationContext.setExecutionResult(result);
					if (exc != null) {
						requestObservation.error(exc);
					}
					requestObservation.stop();
				}
			};
		}
		return super.beginExecution(parameters, state);
	}

	@Nullable
	private static Observation getCurrentObservation(GraphQLContext graphQLContext) {
		return graphQLContext.get(ObservationThreadLocalAccessor.KEY);
	}

	@Override
	public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
			InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
		if (!parameters.isTrivialDataFetcher()
				&& state instanceof RequestObservationInstrumentationState instrumentationState) {
			return environment -> {
				DataFetcherObservationContext observationContext = new DataFetcherObservationContext(parameters.getEnvironment());
				Observation dataFetcherObservation = GraphQlObservationDocumentation.DATA_FETCHER.observation(this.dataFetcherObservationConvention,
						DEFAULT_DATA_FETCHER_CONVENTION, () -> observationContext, this.observationRegistry);
				dataFetcherObservation.parentObservation(getCurrentObservation(environment));
				dataFetcherObservation.start();
				try {
					Object value = dataFetcher.get(environment);
					if (value instanceof CompletionStage<?> completion) {
						return completion.handle((result, error) -> {
							observationContext.setValue(result);
							if (error != null) {
								dataFetcherObservation.error(error);
								dataFetcherObservation.stop();
								throw new CompletionException(error);
							}
							dataFetcherObservation.stop();
							return wrapAsDataFetcherResult(result, dataFetcherObservation);
						});
					}
					else {
						observationContext.setValue(value);
						dataFetcherObservation.stop();
						return wrapAsDataFetcherResult(value, dataFetcherObservation);
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

	@Nullable
	private static Observation getCurrentObservation(DataFetchingEnvironment environment) {
		Observation currentObservation = null;
		if (environment.getLocalContext() != null && environment.getLocalContext() instanceof GraphQLContext) {
			GraphQLContext localContext = environment.getLocalContext();
			currentObservation = localContext.get(ObservationThreadLocalAccessor.KEY);
		}
		if (currentObservation == null) {
			return environment.getGraphQlContext().get(ObservationThreadLocalAccessor.KEY);
		}
		return currentObservation;
	}

	private static DataFetcherResult<?> wrapAsDataFetcherResult(Object value, Observation dataFetcherObservation) {
		if (value instanceof DataFetcherResult<?> result) {
			if (result.getLocalContext() == null) {
				return result.transform(builder -> builder.localContext(GraphQLContext.newContext().of(ObservationThreadLocalAccessor.KEY, dataFetcherObservation).build()));
			}
			else if (result.getLocalContext() instanceof GraphQLContext) {
				((GraphQLContext) result.getLocalContext()).put(ObservationThreadLocalAccessor.KEY, dataFetcherObservation);
			} else {
				logger.debug("Cannot add observation to localContext as it is not a GraphQLContext but a "
						+ result.getLocalContext().getClass().toString());
			}
			return result;
		}
		else {
			return DataFetcherResult.newResult()
					.data(value)
					.localContext(GraphQLContext.newContext().of(ObservationThreadLocalAccessor.KEY, dataFetcherObservation).build())
					.build();
		}

	}


	static class RequestObservationInstrumentationState implements InstrumentationState {

		static final RequestObservationInstrumentationState INSTANCE = new RequestObservationInstrumentationState();

	}

}

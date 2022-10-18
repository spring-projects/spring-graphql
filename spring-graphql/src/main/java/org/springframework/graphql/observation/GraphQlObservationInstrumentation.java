/*
 * Copyright 2020-2022 the original author or authors.
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

import java.util.concurrent.CompletionStage;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * {@link SimpleInstrumentation} that creates {@link Observation observations}
 * for GraphQL requests and data fetcher operations.
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
public class GraphQlObservationInstrumentation extends SimpleInstrumentation {

	private static final String OBSERVATION_KEY = "micrometer.observation";

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
		return new RequestObservationInstrumentationState();
	}

	@Override
	public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters,
			InstrumentationState state) {
		if (state instanceof RequestObservationInstrumentationState instrumentationState) {
			ExecutionRequestObservationContext observationContext = new ExecutionRequestObservationContext(parameters.getExecutionInput());
			Observation requestObservation = instrumentationState.createRequestObservation(this.requestObservationConvention,
					observationContext, this.observationRegistry);
			requestObservation.start();
			return new SimpleInstrumentationContext<>() {
				@Override
				public void onCompleted(ExecutionResult result, Throwable exc) {
					observationContext.setResponse(result);
					if (exc != null) {
						observationContext.setError(exc);
						requestObservation.error(exc);
					}
					else {
						requestObservation.stop();
					}
				}
			};
		}
		return super.beginExecution(parameters, state);
	}

	@Override
	public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
			InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
		if (!parameters.isTrivialDataFetcher()
				&& state instanceof RequestObservationInstrumentationState instrumentationState) {
			return (environment) -> {
				DataFetcherObservationContext observationContext = new DataFetcherObservationContext(parameters.getEnvironment());
				Observation dataFetcherObservation = instrumentationState.createDataFetcherObservation(
						this.dataFetcherObservationConvention, observationContext, this.observationRegistry);
				parameters.getExecutionContext().getGraphQLContext().put(OBSERVATION_KEY, dataFetcherObservation);
				dataFetcherObservation.start();
				try {
					Object value = dataFetcher.get(environment);
					if (value instanceof CompletionStage<?> completion) {
						return completion.whenComplete((result, error) -> {
							if (error != null) {
								dataFetcherObservation.error(error);
							}
							observationContext.setValue(result);
							dataFetcherObservation.stop();
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
		return super.instrumentDataFetcher(dataFetcher, parameters, state);
	}


	static class RequestObservationInstrumentationState implements InstrumentationState {

		private Observation requestObservation;


		Observation createRequestObservation(ExecutionRequestObservationConvention convention,
				ExecutionRequestObservationContext context, ObservationRegistry registry) {
			Observation observation = GraphQlObservationDocumentation.EXECUTION_REQUEST.observation(convention,
					DEFAULT_REQUEST_CONVENTION, () -> context, registry);
			this.requestObservation = observation;
			return observation;
		}

		Observation createDataFetcherObservation(DataFetcherObservationConvention convention,
				DataFetcherObservationContext context, ObservationRegistry registry) {
			Observation dataFetcherObservation = GraphQlObservationDocumentation.DATA_FETCHER.observation(convention,
					DEFAULT_DATA_FETCHER_CONVENTION, () -> context, registry);
			dataFetcherObservation.parentObservation(requestObservation);
			return dataFetcherObservation;
		}

	}

}

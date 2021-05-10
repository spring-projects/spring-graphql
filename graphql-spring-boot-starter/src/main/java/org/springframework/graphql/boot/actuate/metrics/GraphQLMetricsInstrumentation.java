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

package org.springframework.graphql.boot.actuate.metrics;

import java.util.concurrent.CompletionStage;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import org.springframework.boot.actuate.metrics.AutoTimer;

public class GraphQLMetricsInstrumentation extends SimpleInstrumentation {

	private final MeterRegistry registry;

	private final GraphQLTagsProvider tagsProvider;

	private final AutoTimer autoTimer;

	public GraphQLMetricsInstrumentation(MeterRegistry registry, GraphQLTagsProvider tagsProvider, AutoTimer autoTimer) {
		this.registry = registry;
		this.tagsProvider = tagsProvider;
		this.autoTimer = autoTimer;
	}

	@Override
	public InstrumentationState createState() {
		return new RequestMetricsInstrumentationState(this.autoTimer, this.registry);
	}

	@Override
	public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
		if (this.autoTimer.isEnabled()) {
			RequestMetricsInstrumentationState state = parameters.getInstrumentationState();
			state.startTimer();
			return new SimpleInstrumentationContext<ExecutionResult>() {
				@Override
				public void onCompleted(ExecutionResult result, Throwable exc) {
					Iterable<Tag> tags = tagsProvider.getExecutionTags(parameters, result, exc);
					state.tags(tags).stopTimer();
					if (!result.getErrors().isEmpty()) {
						result.getErrors().forEach(error -> {
							registry.counter("graphql.error", tagsProvider.getErrorTags(parameters, error)).increment();
						});
					}
				}
			};
		}
		return super.beginExecution(parameters);
	}

	@Override
	public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
		if (this.autoTimer.isEnabled() && !parameters.isTrivialDataFetcher()) {
			return (environment) -> {
				Timer.Sample sample = Timer.start(this.registry);
				try {
					Object value = dataFetcher.get(environment);
					if (value instanceof CompletionStage<?>) {
						CompletionStage<?> completion = (CompletionStage<?>) value;
						return completion.whenComplete((result, error) -> {
							recordDataFetcherMetric(sample, dataFetcher, parameters, error);
						});
					}
					else {
						recordDataFetcherMetric(sample, dataFetcher, parameters, null);
						return value;
					}

				}
				catch (Throwable throwable) {
					recordDataFetcherMetric(sample, dataFetcher, parameters, throwable);
					throw throwable;
				}
			};
		}
		return super.instrumentDataFetcher(dataFetcher, parameters);
	}

	private void recordDataFetcherMetric(Timer.Sample sample, DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, Throwable throwable) {
		Timer.Builder timer = this.autoTimer.builder("graphql.datafetcher");
		timer.tags(this.tagsProvider.getDataFetchingTags(dataFetcher, parameters, throwable));
		sample.stop(timer.register(this.registry));
	}


	static class RequestMetricsInstrumentationState implements InstrumentationState {

		private final MeterRegistry registry;

		private final Timer.Builder timer;

		private Timer.Sample sample;

		RequestMetricsInstrumentationState(AutoTimer autoTimer, MeterRegistry registry) {
			this.timer = autoTimer.builder("graphql.request");
			this.registry = registry;
		}

		public RequestMetricsInstrumentationState tags(Iterable<Tag> tags) {
			this.timer.tags(tags);
			return this;
		}

		public void startTimer() {
			this.sample = Timer.start(this.registry);
		}

		public void stopTimer() {
			this.sample.stop(this.timer.register(this.registry));
		}
	}

}

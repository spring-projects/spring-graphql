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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.graphql.observation.GraphQlObservationDocumentation.DataFetcherLowCardinalityKeyNames;

/**
 * Default implementation for a {@link DataFetcherObservationConvention}
 * extracting information from a {@link DataFetcherObservationContext}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class DefaultDataFetcherObservationConvention implements DataFetcherObservationConvention {

	private static final String DEFAULT_NAME = "graphql.datafetcher";

	private static final KeyValue OUTCOME_SUCCESS = KeyValue.of(DataFetcherLowCardinalityKeyNames.OUTCOME, "SUCCESS");

	private static final KeyValue OUTCOME_ERROR = KeyValue.of(DataFetcherLowCardinalityKeyNames.OUTCOME, "ERROR");

	private static final KeyValue ERROR_TYPE_NONE = KeyValue.of(DataFetcherLowCardinalityKeyNames.ERROR_TYPE, "NONE");

	private final String name;

	public DefaultDataFetcherObservationConvention() {
		this(DEFAULT_NAME);
	}

	public DefaultDataFetcherObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getContextualName(DataFetcherObservationContext context) {
		return "graphQL field " + context.getEnvironment().getField().getName();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(DataFetcherObservationContext context) {
		return KeyValues.of(outcome(context), fieldName(context), errorType(context));
	}

	protected KeyValue outcome(DataFetcherObservationContext context) {
		if (context.getError() != null) {
			return OUTCOME_ERROR;
		} return OUTCOME_SUCCESS;
	}

	protected KeyValue fieldName(DataFetcherObservationContext context) {
		return KeyValue.of(DataFetcherLowCardinalityKeyNames.FIELD_NAME, context.getEnvironment().getField().getName());
	}

	protected KeyValue errorType(DataFetcherObservationContext context) {
		if (context.getError() != null) {
			return KeyValue.of(DataFetcherLowCardinalityKeyNames.ERROR_TYPE, context.getError().getClass().getSimpleName());
		} return ERROR_TYPE_NONE;
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(DataFetcherObservationContext context) {
		return KeyValues.of(fieldPath(context));
	}

	protected KeyValue fieldPath(DataFetcherObservationContext context) {
		return KeyValue.of(GraphQlObservationDocumentation.DataFetcherHighCardinalityKeyNames.FIELD_PATH,
				context.getEnvironment().getExecutionStepInfo().getPath().toString());
	}
}

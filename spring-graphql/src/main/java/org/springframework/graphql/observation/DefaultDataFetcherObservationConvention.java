/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
		return "graphql field " + context.getEnvironment().getField().getName();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(DataFetcherObservationContext context) {
		return KeyValues.of(outcome(context), fieldName(context), errorType(context));
	}

	protected KeyValue outcome(DataFetcherObservationContext context) {
		if (context.getError() != null) {
			return OUTCOME_ERROR;
		}
		return OUTCOME_SUCCESS;
	}

	protected KeyValue fieldName(DataFetcherObservationContext context) {
		return KeyValue.of(DataFetcherLowCardinalityKeyNames.FIELD_NAME, context.getEnvironment().getField().getName());
	}

	protected KeyValue errorType(DataFetcherObservationContext context) {
		if (context.getError() != null) {
			return KeyValue.of(DataFetcherLowCardinalityKeyNames.ERROR_TYPE, context.getError().getClass().getSimpleName());
		}
		return ERROR_TYPE_NONE;
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

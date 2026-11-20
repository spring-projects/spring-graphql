/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.observation;

import graphql.schema.DataFetchingEnvironment;
import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#DATA_FETCHER data fetching operations}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class DataFetcherObservationContext extends Observation.Context {

	private final DataFetchingEnvironment environment;

	private @Nullable Object value;

	DataFetcherObservationContext(DataFetchingEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Return the data fetching environment provided as an input.
	 */
	public DataFetchingEnvironment getEnvironment() {
		return this.environment;
	}

	/**
	 * Return the value returned by the {@link graphql.schema.DataFetcher}, if any.
	 * @see #getError() for the exception thrown by the data fetcher.
	 */
	public @Nullable Object getValue() {
		return this.value;
	}

	void setValue(@Nullable Object value) {
		this.value = value;
	}
}

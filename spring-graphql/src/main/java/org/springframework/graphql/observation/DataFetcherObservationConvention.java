/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * Interface for an {@link ObservationConvention}
 * for {@link GraphQlObservationDocumentation#DATA_FETCHER data fetching observations}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public interface DataFetcherObservationConvention extends ObservationConvention<DataFetcherObservationContext> {


	@Override
	default boolean supportsContext(Observation.Context context) {
		return context instanceof DataFetcherObservationContext;
	}
}

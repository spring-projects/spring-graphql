/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * Interface for an {@link ObservationConvention} for {@link GraphQlObservationDocumentation#EXECUTION_REQUEST GraphQL requests}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public interface ExecutionRequestObservationConvention extends ObservationConvention<ExecutionRequestObservationContext> {

	@Override
	default boolean supportsContext(Observation.Context context) {
		return context instanceof ExecutionRequestObservationContext;
	}

}

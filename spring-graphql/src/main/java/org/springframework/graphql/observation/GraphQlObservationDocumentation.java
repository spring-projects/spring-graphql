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

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documented {@link io.micrometer.common.KeyValue KeyValues} for {@link graphql.GraphQL GraphQL server observations}.
 * <p>This class is used by automated tools to document KeyValues attached to the GraphQL execution request
 * and data fetcher observations.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public enum GraphQlObservationDocumentation implements ObservationDocumentation {

	/**
	 * Observation created for GraphQL execution requests.
	 */
	EXECUTION_REQUEST {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultExecutionRequestObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return ExecutionRequestLowCardinalityKeyNames.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return ExecutionRequestHighCardinalityKeyNames.values();
		}

	},

	/**
	 * Observation created for {@link InstrumentationFieldFetchParameters#isTrivialDataFetcher() non-trivial}
	 * data fetching operations.
	 */
	DATA_FETCHER {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultDataFetcherObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return DataFetcherLowCardinalityKeyNames.values();
		}
	};

	public enum ExecutionRequestLowCardinalityKeyNames implements KeyName {

		/**
		 * Outcome of the GraphQL request.
		 */
		OUTCOME {
			@Override
			public String asString() {
				return "outcome";
			}
		},

		/**
		 * GraphQL Operation name.
		 */
		OPERATION {
			@Override
			public String asString() {
				return "operation";
			}
		}
	}

	public enum ExecutionRequestHighCardinalityKeyNames implements KeyName {

		/**
		 * {@link graphql.execution.ExecutionId} of the GraphQL request.
		 */
		EXECUTION_ID {
			@Override
			public String asString() {
				return "execution.id";
			}
		}
	}

	public enum DataFetcherLowCardinalityKeyNames implements KeyName {

		/**
		 * Outcome of the GraphQL data fetching operation.
		 */
		OUTCOME {
			@Override
			public String asString() {
				return "outcome";
			}
		},

		/**
		 * Name of the field being fetched.
		 */
		FIELD_NAME {
			@Override
			public String asString() {
				return "field.name";
			}
		},

		/**
		 * Class name of the data fetching error
		 */
		ERROR_TYPE {
			@Override
			public String asString() {
				return "error.type";
			}
		}

	}

	public enum DataFetcherHighCardinalityKeyNames implements KeyName {

		/**
		 * Path to the field being fetched.
		 */
		FIELD_PATH {
			@Override
			public String asString() {
				return "field.path";
			}
		}

	}

}

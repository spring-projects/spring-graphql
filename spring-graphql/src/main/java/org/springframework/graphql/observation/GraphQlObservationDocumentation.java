/*
 * Copyright 2020-present the original author or authors.
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
import org.dataloader.DataLoader;

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
		public String getPrefix() {
			return "graphql";
		}

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
		public String getPrefix() {
			return "graphql";
		}

		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultDataFetcherObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return DataFetcherLowCardinalityKeyNames.values();
		}
	},

	/**
	 * Observation created for {@link org.dataloader.DataLoader} operations.
	 * @since 1.4.0
	 */
	DATA_LOADER {

		@Override
		public String getPrefix() {
			return "graphql";
		}

		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultDataLoaderObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return DataLoaderLowCardinalityKeyNames.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return DataLoaderHighCardinalityKeyNames.values();
		}
	};

	public enum ExecutionRequestLowCardinalityKeyNames implements KeyName {

		/**
		 * Outcome of the GraphQL request.
		 */
		OUTCOME {
			@Override
			public String asString() {
				return "graphql.outcome";
			}
		},

		/**
		 * GraphQL {@link graphql.language.OperationDefinition.Operation Operation type}.
		 */
		OPERATION_TYPE {
			@Override
			public String asString() {
				return "graphql.operation.type";
			}
		}
	}

	public enum ExecutionRequestHighCardinalityKeyNames implements KeyName {

		/**
		 * GraphQL Operation name.
		 */
		OPERATION_NAME {
			@Override
			public String asString() {
				return "graphql.operation.name";
			}
		},

		/**
		 * {@link graphql.execution.ExecutionId} of the GraphQL request.
		 */
		EXECUTION_ID {
			@Override
			public String asString() {
				return "graphql.execution.id";
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
				return "graphql.outcome";
			}
		},

		/**
		 * Name of the field being fetched.
		 */
		FIELD_NAME {
			@Override
			public String asString() {
				return "graphql.field.name";
			}
		},

		/**
		 * Class name of the data fetching error.
		 */
		ERROR_TYPE {
			@Override
			public String asString() {
				return "graphql.error.type";
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
				return "graphql.field.path";
			}
		}

	}

	public enum DataLoaderLowCardinalityKeyNames implements KeyName {

		/**
		 * Class name of the data fetching error.
		 */
		ERROR_TYPE {
			@Override
			public String asString() {
				return "graphql.error.type";
			}
		},

		/**
		 * {@link DataLoader#getName()} of the data loader.
		 */
		LOADER_NAME {
			@Override
			public String asString() {
				return "graphql.loader.name";
			}
		},

		/**
		 * Outcome of the GraphQL data fetching operation.
		 */
		OUTCOME {
			@Override
			public String asString() {
				return "graphql.outcome";
			}
		}

	}

	public enum DataLoaderHighCardinalityKeyNames implements KeyName {

		/**
		 * Size of the list of elements returned by the data loading operation.
		 */
		LOADER_SIZE {
			@Override
			public String asString() {
				return "graphql.loader.size";
			}
		}

	}

}

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

import graphql.schema.DataFetchingEnvironment;
import io.micrometer.observation.Observation;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#DATA_FETCHER data fetching operations}.
 *
 * @author Brian Clozel
 * @since 1.1.0
 */
public class DataFetcherObservationContext extends Observation.Context {

	private final DataFetchingEnvironment environment;

	private Object value;

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
	public Object getValue() {
		return this.value;
	}

	void setValue(Object value) {
		this.value = value;
	}
}

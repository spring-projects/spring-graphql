/*
 * Copyright 2020-2025 the original author or authors.
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

import java.util.List;

import io.micrometer.observation.Observation;
import org.dataloader.BatchLoaderEnvironment;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#DATA_LOADER data loader operations}.
 *
 * @author Brian Clozel
 * @since 1.4.0
 */
public class DataLoaderObservationContext extends Observation.Context {

	private final List<?> keys;

	private final BatchLoaderEnvironment environment;

	private List<?> result = List.of();

	DataLoaderObservationContext(List<?> keys, BatchLoaderEnvironment environment) {
		this.keys = keys;
		this.environment = environment;
	}

	/**
	 * Return the keys for loading by the {@link org.dataloader.DataLoader}.
	 */
	public List<?> getKeys() {
		return this.keys;
	}

	/**
	 * Return the list of values resolved by the {@link org.dataloader.DataLoader},
	 * or an empty list if none were resolved.
	 */
	public List<?> getResult() {
		return this.result;
	}

	/**
	 * Set the list of resolved values by the {@link org.dataloader.DataLoader}.
	 */
	public void setResult(List<?> result) {
		this.result = result;
	}

	/**
	 * Return the {@link BatchLoaderEnvironment environment} given to the batch loading function.
	 */
	public BatchLoaderEnvironment getEnvironment() {
		return this.environment;
	}

}

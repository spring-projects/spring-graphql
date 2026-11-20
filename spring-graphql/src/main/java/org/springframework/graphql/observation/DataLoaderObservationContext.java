/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.observation;

import java.util.List;

import io.micrometer.observation.Observation;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;

/**
 * Context that holds information for metadata collection during observations
 * for {@link GraphQlObservationDocumentation#DATA_LOADER data loader operations}.
 *
 * @author Brian Clozel
 * @since 1.4.0
 */
public class DataLoaderObservationContext extends Observation.Context {

	private final DataLoader<?, ?> dataLoader;

	private final List<?> keys;

	private final BatchLoaderEnvironment environment;

	private List<?> result = List.of();

	DataLoaderObservationContext(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
		this.dataLoader = dataLoader;
		this.keys = keys;
		this.environment = environment;
	}

	/**
	 * Return the {@link DataLoader} being used for the operation.
	 */
	public DataLoader<?, ?> getDataLoader() {
		return this.dataLoader;
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
	 * @param result the values resolved by the data loader
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

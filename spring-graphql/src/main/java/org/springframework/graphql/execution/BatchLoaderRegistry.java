/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.execution;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoaderOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Registry of functions that batch load data values given a set of keys.
 *
 * <p>At request time, each function is registered as a
 * {@link org.dataloader.DataLoader} in the {@link org.dataloader.DataLoaderRegistry}
 * and can be accessed in the data layer to load related entities while avoiding
 * the N+1 select problem.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see <a href="https://www.graphql-java.com/documentation/v16/batching/">Using DataLoader</a>
 * @see org.dataloader.BatchLoader
 * @see org.dataloader.MappedBatchLoader
 * @see org.dataloader.DataLoader
 */
public interface BatchLoaderRegistry {

	/**
	 * Start the registration of a new function for batch loading data values by
	 * specifying the key and value types.
	 * @param keyType the type of the key that identifies the value
	 * @param valueType the type of the data value
	 * @param <K> the key type
	 * @param <V> the value type
	 * @return a spec to complete the registration
	 */
	<K, V> RegistrationSpec<K, V> forTypePair(Class<K> keyType, Class<V> valueType);


	/**
	 * Spec to complete the registration of a batch loading function.
	 *
	 * @param <K> the type of the key that identifies the value
	 * @param <V> the type of the data value
	 */
	interface RegistrationSpec<K, V> {

		/**
		 * Customize the name under which the {@link org.dataloader.DataLoader}
		 * is registered and can be accessed in the data layer.
		 * <p>By default, this is the full class name of the value type.
		 * @param name the name to use
		 * @return a spec to complete the registration
		 */
		RegistrationSpec<K, V> withName(String name);

		/**
		 * Customize the {@link DataLoaderOptions} to use to create the
		 * {@link org.dataloader.DataLoader} via {@link org.dataloader.DataLoaderFactory}.
		 * @param optionsConsumer callback to customize the options, invoked
		 * immediately and given access to the options instance
		 * @return a spec to complete the registration
		 */
		RegistrationSpec<K, V> withOptions(Consumer<DataLoaderOptions> optionsConsumer);

		/**
		 * Replace the {@link DataLoaderOptions} to use to create the
		 * {@link org.dataloader.DataLoader} via {@link org.dataloader.DataLoaderFactory}.
		 * @param options the options to use
		 * @return a spec to complete the registration
		 */
		RegistrationSpec<K, V> withOptions(DataLoaderOptions options);

		/**
		 * Register the give batch loading function.
		 * <p>The values returned from the function must match the order and
		 * the number of keys, with {@code null} for missing values.
		 * Please, see {@link org.dataloader.BatchLoader}.
		 * @param loader the loader function
		 * @see org.dataloader.BatchLoader
		 */
		void registerBatchLoader(BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader);

		/**
		 * A variant of {@link #registerBatchLoader(BiFunction)} that returns a
		 * Map of key-value pairs, which is useful is there aren't values for all keys.
		 * Please see {@link org.dataloader.MappedBatchLoader}.
		 * @param loader the loader function
		 * @see org.dataloader.MappedBatchLoader
		 */
		void registerMappedBatchLoader(BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader);
	}

}

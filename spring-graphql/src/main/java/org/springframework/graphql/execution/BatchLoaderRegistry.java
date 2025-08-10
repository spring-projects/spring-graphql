/*
 * Copyright 2002-present the original author or authors.
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

import graphql.ExecutionInput;
import org.dataloader.BatchLoaderContextProvider;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoaderOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Registry for functions to batch load data values, given a set of keys.
 *
 * <p>At request time, each function is registered as a
 * {@link org.dataloader.DataLoader} in the {@link org.dataloader.DataLoaderRegistry}
 * and can be accessed in the data layer to load related entities while avoiding
 * the N+1 select problem.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 * @see <a href="https://www.graphql-java.com/documentation/batching/">Using DataLoader</a>
 * @see org.dataloader.BatchLoader
 * @see org.dataloader.MappedBatchLoader
 * @see org.dataloader.DataLoader
 */
public interface BatchLoaderRegistry extends DataLoaderRegistrar {

	/**
	 * Begin the registration of a new batch load function by specifying the
	 * types of the keys and values that will be used as input and output.
	 *
	 * <p>When this method is used, the name for the
	 * {@link org.dataloader.DataLoader} is automatically set as defined in
	 * {@link RegistrationSpec#withName(String)}, and likewise,
	 * {@code @SchemaMapping} handler methods can transparently locate and
	 * inject a {@code DataLoader<T>} argument based on the generic type
	 * {@code <T>}.
	 * @param keyType the type of keys that will be used as input
	 * @param valueType the type of value that will be returned as output
	 * @param <K> the key type
	 * @param <V> the value type
	 * @return a spec to complete the registration
	 */
	<K, V> RegistrationSpec<K, V> forTypePair(Class<K> keyType, Class<V> valueType);

	/**
	 * Begin the registration of a new batch load function by specifying the
	 * name for the {@link org.dataloader.DataLoader}.
	 *
	 * <p><strong>Note:</strong> when this method is used, the parameter name
	 * of a {@code DataLoader<T>} argument in a {@code @SchemaMapping} handler
	 * method needs to match the name given here.
	 * @param name the name to use to register a {@code DataLoader}
	 * @param <K> the type of keys that will be used as input
	 * @param <V> the type of values that will be used as output
	 * @return a spec to complete the registration
	 */
	<K, V> RegistrationSpec<K, V> forName(String name);


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
		 * <p>By default, this is the full class name of the value type, if the
		 * value type is specified via {@link #forTypePair(Class, Class)}.
		 * @param name the name to use
		 * @return a spec to complete the registration
		 */
		RegistrationSpec<K, V> withName(String name);

		/**
		 * Customize the {@link DataLoaderOptions} to use to create the
		 * {@link org.dataloader.DataLoader} via {@link org.dataloader.DataLoaderFactory}.
		 * <p><strong>Note:</strong> Do not set
		 * {@link DataLoaderOptions.Builder#setBatchLoaderContextProvider(BatchLoaderContextProvider)}
		 * as this will be set later to a provider that returns the context from
		 * {@link ExecutionInput#getGraphQLContext()}, so that batch loading
		 * functions and data fetchers can rely on access to the same context.
		 * @param optionsBuilderConsumer callback to customize the options, invoked
		 * immediately and given access to the options builder instance
		 * @return a spec to complete the registration
		 * @since 1.4.0
		 */
		RegistrationSpec<K, V> withOptions(Consumer<DataLoaderOptions.Builder> optionsBuilderConsumer);

		/**
		 * Set the {@link DataLoaderOptions} to use to create the
		 * {@link org.dataloader.DataLoader} via {@link org.dataloader.DataLoaderFactory}.
		 * <p><strong>Note:</strong> Do not set
		 * {@link DataLoaderOptions#setBatchLoaderContextProvider(BatchLoaderContextProvider)}
		 * as this will be set later to a provider that returns the context from
		 * {@link ExecutionInput#getGraphQLContext()}, so that batch loading
		 * functions and data fetchers can rely on access to the same context.
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

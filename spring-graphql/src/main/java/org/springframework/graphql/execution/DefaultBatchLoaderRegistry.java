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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoaderWithContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A default implementation of {@link BatchLoaderRegistry} that accepts
 * registrations, and also an implementation of {@link DataLoaderRegistrar} to
 * apply those registrations to a {@link DataLoaderRegistry}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultBatchLoaderRegistry implements BatchLoaderRegistry, DataLoaderRegistrar {

	private final List<ReactorBatchLoader<?,?>> loaders = new ArrayList<>();

	private final List<ReactorMappedBatchLoader<?,?>> mappedLoaders = new ArrayList<>();


	@Override
	public <K, V> RegistrationSpec<K, V> forTypePair(Class<K> keyType, Class<V> valueType) {
		return new DefaultRegistrationSpec<>(valueType);
	}

	@Override
	public <K, V> RegistrationSpec<K, V> forName(String name) {
		return new DefaultRegistrationSpec<>(name);
	}

	@Override
	public void registerDataLoaders(DataLoaderRegistry registry) {
		for (ReactorBatchLoader<?, ?> loader : this.loaders) {
			DataLoader<?, ?> dataLoader = DataLoaderFactory.newDataLoader(loader, loader.getOptions());
			registerDataLoader(loader.getName(), dataLoader, registry);
		}
		for (ReactorMappedBatchLoader<?, ?> loader : this.mappedLoaders) {
			DataLoader<?, ?> dataLoader = DataLoaderFactory.newMappedDataLoader(loader, loader.getOptions());
			registerDataLoader(loader.getName(), dataLoader, registry);
		}
	}

	private void registerDataLoader(String name, DataLoader<?, ?> dataLoader, DataLoaderRegistry registry) {
		if (registry.getDataLoader(name) != null) {
			throw new IllegalStateException("More than one DataLoader named '" + name + "'");
		}
		registry.register(name, dataLoader);
	}


	private class DefaultRegistrationSpec<K, V> implements RegistrationSpec<K, V> {

		@Nullable
		private final Class<?> valueType;

		@Nullable
		private String name;

		private DataLoaderOptions options = DataLoaderOptions.newOptions();

		public DefaultRegistrationSpec(Class<V> valueType) {
			this.valueType = valueType;
		}

		public DefaultRegistrationSpec(String name) {
			this.name = name;
			this.valueType = null;
		}

		@Override
		public RegistrationSpec<K, V> withName(String name) {
			this.name = name;
			return this;
		}

		@Override
		public RegistrationSpec<K, V> withOptions(Consumer<DataLoaderOptions> optionsConsumer) {
			optionsConsumer.accept(this.options);
			return this;
		}

		@Override
		public RegistrationSpec<K, V> withOptions(DataLoaderOptions options) {
			this.options = options;
			return this;
		}

		@Override
		public void registerBatchLoader(BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader) {
			DefaultBatchLoaderRegistry.this.loaders.add(
					new ReactorBatchLoader<>(initName(), loader, this.options));
		}

		@Override
		public void registerMappedBatchLoader(BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader) {
			DefaultBatchLoaderRegistry.this.mappedLoaders.add(
					new ReactorMappedBatchLoader<>(initName(), loader, this.options));
		}

		private String initName() {
			if (StringUtils.hasText(this.name)) {
				return this.name;
			}
			Assert.notNull(this.valueType, "Value type not available to select a default DataLoader name.");
			return (StringUtils.hasText(this.name) ? this.name : this.valueType.getName());
		}
	}


	private static class ReactorBatchLoader<K, V> implements BatchLoaderWithContext<K, V> {

		private final String name;

		private final BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader;

		private final DataLoaderOptions options;

		private ReactorBatchLoader(String name,
				BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader,
				DataLoaderOptions options) {

			this.name = name;
			this.loader = loader;
			this.options = options;
		}

		public String getName() {
			return this.name;
		}

		public DataLoaderOptions getOptions() {
			return this.options;
		}

		@Override
		public CompletionStage<List<V>> load(List<K> keys, BatchLoaderEnvironment environment) {
			return this.loader.apply(keys, environment).collectList().toFuture();
		}
	}


	private static class ReactorMappedBatchLoader<K, V> implements MappedBatchLoaderWithContext<K, V> {

		private final String name;

		private final BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader;

		private final DataLoaderOptions options;

		private ReactorMappedBatchLoader(String name,
				BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader,
				DataLoaderOptions options) {

			this.name = name;
			this.loader = loader;
			this.options = options;
		}

		public String getName() {
			return this.name;
		}

		public DataLoaderOptions getOptions() {
			return this.options;
		}

		@Override
		public CompletionStage<Map<K, V>> load(Set<K> keys, BatchLoaderEnvironment environment) {
			return this.loader.apply(keys, environment).toFuture();
		}
	}

}

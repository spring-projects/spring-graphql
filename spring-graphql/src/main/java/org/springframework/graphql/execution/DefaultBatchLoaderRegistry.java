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

import graphql.GraphQLContext;
import org.dataloader.BatchLoaderContextProvider;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoaderWithContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

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
public class DefaultBatchLoaderRegistry implements BatchLoaderRegistry {

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
	public void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context) {
		BatchLoaderContextProvider contextProvider = () -> context;
		DataLoaderOptions defaultOptions = DataLoaderOptions.newOptions().setBatchLoaderContextProvider(contextProvider);
		for (ReactorBatchLoader<?, ?> loader : this.loaders) {
			DataLoaderOptions options = loader.getOptionsOrDefault(contextProvider, defaultOptions);
			DataLoader<?, ?> dataLoader = DataLoaderFactory.newDataLoader(loader, options);
			registerDataLoader(loader.getName(), dataLoader, registry);
		}
		for (ReactorMappedBatchLoader<?, ?> loader : this.mappedLoaders) {
			DataLoaderOptions options = loader.getOptionsOrDefault(contextProvider, defaultOptions);
			DataLoader<?, ?> dataLoader = DataLoaderFactory.newMappedDataLoader(loader, options);
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

		@Nullable
		private DataLoaderOptions options;

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
			this.options = (this.options != null ? this.options : DataLoaderOptions.newOptions());
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


	/**
	 * {@link BatchLoaderWithContext} that delegates to a {@link Flux} batch
	 * loading function and exposes Reactor context to it.
	 */
	private static class ReactorBatchLoader<K, V> implements BatchLoaderWithContext<K, V> {

		private final String name;

		private final BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader;

		@Nullable
		private final DataLoaderOptions options;

		private ReactorBatchLoader(String name,
				BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader,
				@Nullable DataLoaderOptions options) {

			this.name = name;
			this.loader = loader;
			this.options = options;
		}

		public String getName() {
			return this.name;
		}

		public DataLoaderOptions getOptionsOrDefault(
				BatchLoaderContextProvider provider, DataLoaderOptions defaultOptions) {

			if (this.options != null) {
				return new DataLoaderOptions(this.options).setBatchLoaderContextProvider(provider);
			}

			return defaultOptions;
		}

		@Override
		public CompletionStage<List<V>> load(List<K> keys, BatchLoaderEnvironment environment) {
			ContextView contextView = ReactorContextManager.getReactorContext(environment.getContext());
			try {
				ReactorContextManager.restoreThreadLocalValues(contextView);
				return this.loader.apply(keys, environment).collectList().contextWrite(contextView).toFuture();
			}
			finally {
				ReactorContextManager.resetThreadLocalValues(contextView);
			}
		}

	}


	/**
	 * {@link MappedBatchLoaderWithContext} that delegates to a {@link Mono}
	 * batch loading function and exposes Reactor context to it.
	 */
	private static class ReactorMappedBatchLoader<K, V> implements MappedBatchLoaderWithContext<K, V> {

		private final String name;

		private final BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader;

		@Nullable
		private final DataLoaderOptions options;

		private ReactorMappedBatchLoader(String name,
				BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader,
				@Nullable DataLoaderOptions options) {

			this.name = name;
			this.loader = loader;
			this.options = options;
		}

		public String getName() {
			return this.name;
		}

		public DataLoaderOptions getOptionsOrDefault(
				BatchLoaderContextProvider provider, DataLoaderOptions defaultOptions) {

			if (this.options != null) {
				return new DataLoaderOptions(this.options).setBatchLoaderContextProvider(provider);
			}

			return defaultOptions;
		}

		@Override
		public CompletionStage<Map<K, V>> load(Set<K> keys, BatchLoaderEnvironment environment) {
			ContextView contextView = ReactorContextManager.getReactorContext(environment.getContext());
			try {
				ReactorContextManager.restoreThreadLocalValues(contextView);
				return this.loader.apply(keys, environment).contextWrite(contextView).toFuture();
			}
			finally {
				ReactorContextManager.resetThreadLocalValues(contextView);
			}
		}

	}

}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import graphql.GraphQLContext;
import io.micrometer.context.ContextSnapshot;
import org.dataloader.BatchLoaderContextProvider;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoaderWithContext;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link BatchLoaderRegistry} that stores batch loader
 * registrations. Also, an implementation of {@link DataLoaderRegistrar} that
 * registers the batch loaders as {@link DataLoader}s in {@link DataLoaderRegistry}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultBatchLoaderRegistry implements BatchLoaderRegistry {

	private final List<ReactorBatchLoader<?, ?>> loaders = new ArrayList<>();

	private final List<ReactorMappedBatchLoader<?, ?>> mappedLoaders = new ArrayList<>();

	private final Supplier<DataLoaderOptions> defaultOptionsSupplier;


	/**
	 * Default constructor.
	 */
	public DefaultBatchLoaderRegistry() {
		this(DataLoaderOptions::newDefaultOptions);
	}

	/**
	 * Constructor with a default {@link DataLoaderOptions} supplier to use as
	 * a starting point for batch loader registrations.
	 * @param defaultOptionsSupplier a supplier for default dataloader options
	 * @since 1.1.0
	 */
	public DefaultBatchLoaderRegistry(Supplier<DataLoaderOptions> defaultOptionsSupplier) {
		Assert.notNull(defaultOptionsSupplier, "'defaultOptionsSupplier' is required");
		this.defaultOptionsSupplier = defaultOptionsSupplier;
	}


	@Override
	public <K, V> RegistrationSpec<K, V> forTypePair(Class<K> keyType, Class<V> valueType) {
		return new DefaultRegistrationSpec<>(valueType);
	}

	@Override
	public <K, V> RegistrationSpec<K, V> forName(String name) {
		return new DefaultRegistrationSpec<>(name);
	}

	@Override
	public boolean hasRegistrations() {
		return (!this.loaders.isEmpty() || !this.mappedLoaders.isEmpty());
	}

	@Override
	public void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context) {
		BatchLoaderContextProvider contextProvider = () -> context;
		for (ReactorBatchLoader<?, ?> loader : this.loaders) {
			DataLoaderOptions options = loader.getOptions()
					.transform((opt) -> opt.setBatchLoaderContextProvider(contextProvider));
			DataLoader<?, ?> dataLoader = DataLoaderFactory.newDataLoader(loader.getName(), loader, options);
			registerDataLoader(dataLoader, registry);
		}
		for (ReactorMappedBatchLoader<?, ?> loader : this.mappedLoaders) {
			DataLoaderOptions options = loader.getOptions()
					.transform((opt) -> opt.setBatchLoaderContextProvider(contextProvider));
			DataLoader<?, ?> dataLoader = DataLoaderFactory.newMappedDataLoader(loader.getName(), loader, options);
			registerDataLoader(dataLoader, registry);
		}
	}

	@SuppressWarnings("NullAway") // DataLoaderRegistry#getDataLoader should be @Nullable
	private void registerDataLoader(DataLoader<?, ?> dataLoader, DataLoaderRegistry registry) {
		if (registry.getDataLoader(dataLoader.getName()) != null) {
			throw new IllegalStateException("More than one DataLoader named '" + dataLoader.getName() + "'");
		}
		registry.register(dataLoader.getName(), dataLoader);
	}


	private class DefaultRegistrationSpec<K, V> implements RegistrationSpec<K, V> {

		private final @Nullable Class<?> valueType;

		private @Nullable String name;

		private @Nullable DataLoaderOptions options;

		private @Nullable Consumer<DataLoaderOptions.Builder> optionsBuilderConsumer;

		DefaultRegistrationSpec(Class<V> valueType) {
			this.valueType = valueType;
		}

		DefaultRegistrationSpec(String name) {
			this.name = name;
			this.valueType = null;
		}

		@Override
		public RegistrationSpec<K, V> withName(String name) {
			this.name = name;
			return this;
		}

		@Override
		public RegistrationSpec<K, V> withOptions(Consumer<DataLoaderOptions.Builder> optionsBuilderConsumer) {
			this.optionsBuilderConsumer = (this.optionsBuilderConsumer != null) ?
					this.optionsBuilderConsumer.andThen(optionsBuilderConsumer) : optionsBuilderConsumer;
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
					new ReactorBatchLoader<>(initName(), loader, initOptionsSupplier()));
		}

		@Override
		public void registerMappedBatchLoader(BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader) {
			DefaultBatchLoaderRegistry.this.mappedLoaders.add(
					new ReactorMappedBatchLoader<>(initName(), loader, initOptionsSupplier()));
		}

		private String initName() {
			if (StringUtils.hasText(this.name)) {
				return this.name;
			}
			Assert.notNull(this.valueType, "Value type not available to select a default DataLoader name.");
			return (StringUtils.hasText(this.name) ? this.name : this.valueType.getName());
		}

		private Supplier<DataLoaderOptions> initOptionsSupplier() {
			return () -> {
				DataLoaderOptions.Builder builder;
				if (this.options != null) {
					builder = DataLoaderOptions.newOptions(this.options);
				}
				else {
					builder = DataLoaderOptions.newOptions(DefaultBatchLoaderRegistry.this.defaultOptionsSupplier.get());
				}
				if (this.optionsBuilderConsumer != null) {
					this.optionsBuilderConsumer.accept(builder);
				}
				return builder.build();
			};
		}

	}


	/**
	 * {@link BatchLoaderWithContext} that delegates to a {@link Flux} batch
	 * loading function and exposes Reactor context to it.
	 */
	private static final class ReactorBatchLoader<K, V> implements BatchLoaderWithContext<K, V> {

		private final String name;

		private final BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader;

		private final Supplier<DataLoaderOptions> optionsSupplier;

		private ReactorBatchLoader(String name,
				BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader,
				Supplier<DataLoaderOptions> optionsSupplier) {

			this.name = name;
			this.loader = loader;
			this.optionsSupplier = optionsSupplier;
		}

		String getName() {
			return this.name;
		}

		DataLoaderOptions getOptions() {
			return this.optionsSupplier.get();
		}

		@Override
		public CompletionStage<List<V>> load(List<K> keys, BatchLoaderEnvironment environment) {
			GraphQLContext graphQLContext = environment.getContext();
			Assert.state(graphQLContext != null, "No GraphQLContext available");
			ContextSnapshot snapshot = ContextPropagationHelper.captureFrom(graphQLContext);
			try {
				return snapshot.wrap(() ->
								this.loader.apply(keys, environment)
										.collectList()
										.contextWrite(snapshot::updateContext)
										.toFuture())
						.call();
			}
			catch (Exception ex) {
				return CompletableFuture.failedFuture(ex);
			}
		}
	}


	/**
	 * {@link MappedBatchLoaderWithContext} that delegates to a {@link Mono}
	 * batch loading function and exposes Reactor context to it.
	 */
	private static final class ReactorMappedBatchLoader<K, V> implements MappedBatchLoaderWithContext<K, V> {

		private final String name;

		private final BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader;

		private final Supplier<DataLoaderOptions> optionsSupplier;

		private ReactorMappedBatchLoader(String name,
				BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader,
				Supplier<DataLoaderOptions> optionsSupplier) {

			this.name = name;
			this.loader = loader;
			this.optionsSupplier = optionsSupplier;
		}

		String getName() {
			return this.name;
		}

		DataLoaderOptions getOptions() {
			return this.optionsSupplier.get();
		}

		@Override
		public CompletionStage<Map<K, V>> load(Set<K> keys, BatchLoaderEnvironment environment) {
			GraphQLContext graphQLContext = environment.getContext();
			Assert.state(graphQLContext != null, "No GraphQLContext available");
			ContextSnapshot snapshot = ContextPropagationHelper.captureFrom(graphQLContext);
			try {
				return snapshot.wrap(() ->
								this.loader.apply(keys, environment)
										.contextWrite(snapshot::updateContext)
										.toFuture())
						.call();
			}
			catch (Exception ex) {
				return CompletableFuture.failedFuture(ex);
			}
		}

	}

}

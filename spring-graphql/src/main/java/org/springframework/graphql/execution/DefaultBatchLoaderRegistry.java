/*
 * Copyright 2002-2022 the original author or authors.
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
public class DefaultBatchLoaderRegistry implements BatchLoaderRegistry {

	private final List<ReactorBatchLoader<?,?>> loaders = new ArrayList<>();

	private final List<ReactorMappedBatchLoader<?,?>> mappedLoaders = new ArrayList<>();

	private final Supplier<DataLoaderOptions> defaultOptionsSupplier;


	/**
	 * Default constructor
	 */
	public DefaultBatchLoaderRegistry() {
		this(DataLoaderOptions::newOptions);
	}

	/**
	 * Constructor with a default {@link DataLoaderOptions} supplier to use as
	 * a starting point for all registrations.
	 * @since 1.1
	 */
	public DefaultBatchLoaderRegistry(Supplier<DataLoaderOptions> defaultOptionsSupplier) {
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
	public void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context) {
		BatchLoaderContextProvider contextProvider = () -> context;
		DataLoaderOptions defaultOptions = this.defaultOptionsSupplier.get();
		for (ReactorBatchLoader<?, ?> loader : this.loaders) {
			DataLoaderOptions options = loader.getOptions();
			options = (options != null ? options : defaultOptions).setBatchLoaderContextProvider(contextProvider);
			DataLoader<?, ?> dataLoader = DataLoaderFactory.newDataLoader(loader, options);
			registerDataLoader(loader.getName(), dataLoader, registry);
		}
		for (ReactorMappedBatchLoader<?, ?> loader : this.mappedLoaders) {
			DataLoaderOptions options = loader.getOptions();
			options = (options != null ? options : defaultOptions).setBatchLoaderContextProvider(contextProvider);
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

		@Nullable
		private Consumer<DataLoaderOptions> optionsConsumer;

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
			this.optionsConsumer = (this.optionsConsumer != null ?
					this.optionsConsumer.andThen(optionsConsumer) : optionsConsumer);
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

		@Nullable
		private Supplier<DataLoaderOptions> initOptionsSupplier() {
			if (this.options == null && this.optionsConsumer == null) {
				return null;
			}

			Supplier<DataLoaderOptions> optionsSupplier =
					(this.options != null ? () -> this.options : defaultOptionsSupplier);

			if (this.optionsConsumer == null) {
				return optionsSupplier;
			}

			return () -> {
				DataLoaderOptions options = optionsSupplier.get();
				this.optionsConsumer.accept(options);
				return options;
			};
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
		private final Supplier<DataLoaderOptions> optionsSupplier;

		private ReactorBatchLoader(String name,
				BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader,
				@Nullable Supplier<DataLoaderOptions> optionsSupplier) {

			this.name = name;
			this.loader = loader;
			this.optionsSupplier = optionsSupplier;
		}

		public String getName() {
			return this.name;
		}

		@Nullable
		public DataLoaderOptions getOptions() {
			return (this.optionsSupplier != null ? this.optionsSupplier.get() : null);
		}

		@Override
		public CompletionStage<List<V>> load(List<K> keys, BatchLoaderEnvironment environment) {
			GraphQLContext graphQLContext = environment.getContext();
			ContextSnapshot snapshot = ContextSnapshot.captureFrom(graphQLContext);
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
	private static class ReactorMappedBatchLoader<K, V> implements MappedBatchLoaderWithContext<K, V> {

		private final String name;

		private final BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader;

		@Nullable
		private final Supplier<DataLoaderOptions> optionsSupplier;

		private ReactorMappedBatchLoader(String name,
				BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader,
				@Nullable Supplier<DataLoaderOptions> optionsSupplier) {

			this.name = name;
			this.loader = loader;
			this.optionsSupplier = optionsSupplier;
		}

		public String getName() {
			return this.name;
		}

		@Nullable
		public DataLoaderOptions getOptions() {
			return (this.optionsSupplier != null ? this.optionsSupplier.get() : null);
		}

		@Override
		public CompletionStage<Map<K, V>> load(Set<K> keys, BatchLoaderEnvironment environment) {
			GraphQLContext graphQLContext = environment.getContext();
			ContextSnapshot snapshot = ContextSnapshot.captureFrom(graphQLContext);
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

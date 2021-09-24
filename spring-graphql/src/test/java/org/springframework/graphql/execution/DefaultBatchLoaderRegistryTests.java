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

import java.util.Map;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.NoOpStatisticsCollector;
import org.dataloader.stats.StatisticsCollector;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.Book;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Unit tests for {@link DefaultBatchLoaderRegistry}.
 */
public class DefaultBatchLoaderRegistryTests {

	private final DefaultBatchLoaderRegistry batchLoaderRegistry = new DefaultBatchLoaderRegistry();

	private final DataLoaderRegistry dataLoaderRegistry = DataLoaderRegistry.newRegistry().build();


	@Test
	void batchLoader() {
		this.batchLoaderRegistry.forTypePair(String.class, Book.class).registerBatchLoader((keys, environment) -> Flux.empty());
		this.batchLoaderRegistry.registerDataLoaders(this.dataLoaderRegistry);

		Map<String, DataLoader<?, ?>> map = this.dataLoaderRegistry.getDataLoadersMap();
		assertThat(map).hasSize(1).containsKey(Book.class.getName());
	}

	@Test
	void mappedBatchLoader() {
		this.batchLoaderRegistry
				.forTypePair(String.class, Book.class)
				.registerMappedBatchLoader((keys, environment) -> Mono.empty());

		this.batchLoaderRegistry.registerDataLoaders(this.dataLoaderRegistry);

		Map<String, DataLoader<?, ?>> map = this.dataLoaderRegistry.getDataLoadersMap();
		assertThat(map).hasSize(1).containsKey(Book.class.getName());
	}

	@Test
	void batchLoaderWithCustomNameAndOptions() {
		String name = "myLoader";
		StatisticsCollector collector = new NoOpStatisticsCollector();

		this.batchLoaderRegistry.forName(name)
				.withOptions(options -> options.setStatisticsCollector(() -> collector))
				.registerBatchLoader((keys, environment) -> Flux.empty());

		this.batchLoaderRegistry.registerDataLoaders(this.dataLoaderRegistry);

		Map<String, DataLoader<?, ?>> map = dataLoaderRegistry.getDataLoadersMap();
		assertThat(map).hasSize(1).containsKey(name);
		assertThat(map.get(name).getStatistics()).isSameAs(collector.getStatistics());
	}

}

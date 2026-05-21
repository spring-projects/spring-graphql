/*
 * Copyright 2026-present the original author or authors.
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


import java.util.ArrayList;
import java.util.List;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link DefaultDataLoaderObservationConvention}.
 *
 * @author Brian Clozel
 */
class DefaultDataLoaderObservationConventionTests {

	private final DefaultDataLoaderObservationConvention convention = new DefaultDataLoaderObservationConvention();

	@Test
	void hasDefaultName() {
		assertThat(convention.getName()).isEqualTo("graphql.dataloader");
	}

	@Test
	void contextualNameForValue() {
		DataLoader<?, ?> dataLoader = mock(DataLoader.class);
		DataLoaderObservationContext observationContext = getLoaderObservationContext(dataLoader);
		ArrayList<Project> values = new ArrayList<>();
		values.add(null);
		values.add(new Project("spring-graphql"));
		observationContext.setResult(values);

		String contextualName = this.convention.getContextualName(observationContext);
		assertThat(contextualName).isEqualTo("graphql dataloader project");
	}

	@Test
	void contextualNameForNullValues() {
		DataLoader<?, ?> dataLoader = mock(DataLoader.class);
		DataLoaderObservationContext observationContext = getLoaderObservationContext(dataLoader);
		ArrayList<Project> values = new ArrayList<>();
		values.add(null);
		values.add(null);
		observationContext.setResult(values);

		String contextualName = this.convention.getContextualName(observationContext);
		assertThat(contextualName).isEqualTo("graphql dataloader");
	}

	@Test
	void lowCardinalityKeyValues() {
		DataLoader<?, ?> dataLoader = mock(DataLoader.class);
		given(dataLoader.getName()).willReturn("project");
		DataLoaderObservationContext observationContext = getLoaderObservationContext(dataLoader);

		KeyValues keyValues = this.convention.getLowCardinalityKeyValues(observationContext);
		assertThat(keyValues).contains(
				KeyValue.of("graphql.outcome", "SUCCESS"),
				KeyValue.of("graphql.error.type", "NONE"),
				KeyValue.of("graphql.loader.name", "project")
		);
	}

	@Test
	void lowCardinalityKeyValuesWithError() {
		DataLoader<?, ?> dataLoader = mock(DataLoader.class);
		given(dataLoader.getName()).willReturn("project");
		DataLoaderObservationContext observationContext = getLoaderObservationContext(dataLoader);
		observationContext.setError(new IllegalArgumentException("test error"));

		KeyValues keyValues = this.convention.getLowCardinalityKeyValues(observationContext);
		assertThat(keyValues).contains(
				KeyValue.of("graphql.outcome", "ERROR"),
				KeyValue.of("graphql.error.type", "IllegalArgumentException"),
				KeyValue.of("graphql.loader.name", "project")
		);
	}

	@Test
	void lowCardinalityKeyValuesWithUnknownLoaderName() {
		DataLoader<?, ?> dataLoader = mock(DataLoader.class);
		given(dataLoader.getName()).willReturn("");
		DataLoaderObservationContext observationContext = getLoaderObservationContext(dataLoader);

		KeyValues keyValues = this.convention.getLowCardinalityKeyValues(observationContext);
		assertThat(keyValues).contains(
				KeyValue.of("graphql.outcome", "SUCCESS"),
				KeyValue.of("graphql.error.type", "NONE"),
				KeyValue.of("graphql.loader.name", "unknown")
		);
	}

	@Test
	void highCardinalityKeyValues() {
		DataLoader<?, ?> dataLoader = mock(DataLoader.class);
		DataLoaderObservationContext observationContext = getLoaderObservationContext(dataLoader);
		observationContext.setResult(List.of(new Project("spring-graphql"), new Project("spring-framework")));

		KeyValues keyValues = this.convention.getHighCardinalityKeyValues(observationContext);
		assertThat(keyValues).contains(
				KeyValue.of("graphql.loader.size", "2")
		);
	}

	private DataLoaderObservationContext getLoaderObservationContext(DataLoader<?, ?> dataLoader) {
		return new DataLoaderObservationContext(dataLoader,
				List.of(1, 2),
				BatchLoaderEnvironment.newBatchLoaderEnvironment().build());
	}

	record Project(String name) {
	}

}

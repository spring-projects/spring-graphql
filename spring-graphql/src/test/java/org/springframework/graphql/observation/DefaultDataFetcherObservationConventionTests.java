/*
 * Copyright 2020-2022 the original author or authors.
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


import java.util.function.Consumer;

import graphql.GraphQLContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLObjectType;
import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultDataFetcherObservationConvention}
 *
 * @author Brian Clozel
 */
class DefaultDataFetcherObservationConventionTests {

	DataFetcherObservationConvention convention = new DefaultDataFetcherObservationConvention();

	@Test
	void nameHasDefault() {
		assertThat(this.convention.getName()).isEqualTo("graphql.datafetcher");
	}

	@Test
	void nameCanBeCustomized() {
		String customName = "graphql.custom";
		DefaultDataFetcherObservationConvention customConvention = new DefaultDataFetcherObservationConvention(customName);
		assertThat(customConvention.getName()).isEqualTo(customName);
	}

	@Test
	void contextualNameContainsFieldName() {
		DataFetchingEnvironment environment = createDataFetchingEnvironment(builder -> {
			builder.mergedField(MergedField.newMergedField(Field.newField("project").build()).build());
		});
		DataFetcherObservationContext context = new DataFetcherObservationContext(environment);
		assertThat(this.convention.getContextualName(context)).isEqualTo("graphQL field project");
	}

	@Test
	void fieldNameKeyValueIsPresent() {
		DataFetchingEnvironment environment = createDataFetchingEnvironment(builder -> {
			builder.mergedField(MergedField.newMergedField(Field.newField("project").build()).build());
		});
		DataFetcherObservationContext context = new DataFetcherObservationContext(environment);
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("field.name", "project"));
	}

	@Test
	void errorTypeKeyValueIsPresent() {
		DataFetchingEnvironment environment = createDataFetchingEnvironment(builder -> {
			builder.mergedField(MergedField.newMergedField(Field.newField("project").build()).build());
		});
		DataFetcherObservationContext context = new DataFetcherObservationContext(environment);
		context.setError(new IllegalStateException("custom data fetching failure"));
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("error.type", "IllegalStateException"));
	}

	@Test
	void fieldPathKeyValueIsPresent() {
		DataFetchingEnvironment environment = createDataFetchingEnvironment(builder -> {
			builder.mergedField(MergedField.newMergedField(Field.newField("project").build()).build())
					.executionStepInfo(ExecutionStepInfo.newExecutionStepInfo().type(new GraphQLObjectType.Builder().name("project").build())
							.path(ResultPath.parse("/projectBySlug/releases")).build());
		});
		DataFetcherObservationContext context = new DataFetcherObservationContext(environment);
		context.setError(new IllegalStateException("custom data fetching failure"));
		assertThat(this.convention.getHighCardinalityKeyValues(context)).contains(KeyValue.of("field.path", "/projectBySlug/releases"));
	}

	private DataFetchingEnvironment createDataFetchingEnvironment(Consumer<DataFetchingEnvironmentImpl.Builder> consumer) {
		GraphQLContext graphQLContext = new GraphQLContext.Builder().build();
		DataFetchingEnvironmentImpl.Builder builder = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
				.graphQLContext(graphQLContext);
		consumer.accept(builder);
		return builder.build();
	}
}
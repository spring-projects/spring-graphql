/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
		assertThat(this.convention.getContextualName(context)).isEqualTo("graphql field project");
	}

	@Test
	void fieldNameKeyValueIsPresent() {
		DataFetchingEnvironment environment = createDataFetchingEnvironment(builder -> {
			builder.mergedField(MergedField.newMergedField(Field.newField("project").build()).build());
		});
		DataFetcherObservationContext context = new DataFetcherObservationContext(environment);
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("graphql.field.name", "project"));
	}

	@Test
	void errorTypeKeyValueIsPresent() {
		DataFetchingEnvironment environment = createDataFetchingEnvironment(builder -> {
			builder.mergedField(MergedField.newMergedField(Field.newField("project").build()).build());
		});
		DataFetcherObservationContext context = new DataFetcherObservationContext(environment);
		context.setError(new IllegalStateException("custom data fetching failure"));
		assertThat(this.convention.getLowCardinalityKeyValues(context)).contains(KeyValue.of("graphql.error.type", "IllegalStateException"));
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
		assertThat(this.convention.getHighCardinalityKeyValues(context)).contains(KeyValue.of("graphql.field.path", "/projectBySlug/releases"));
	}

	private DataFetchingEnvironment createDataFetchingEnvironment(Consumer<DataFetchingEnvironmentImpl.Builder> consumer) {
		GraphQLContext graphQLContext = new GraphQLContext.Builder().build();
		DataFetchingEnvironmentImpl.Builder builder = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
				.graphQLContext(graphQLContext);
		consumer.accept(builder);
		return builder.build();
	}
}

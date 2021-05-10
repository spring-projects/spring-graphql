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

package org.springframework.graphql.boot.actuate.metrics;

import java.util.List;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

public class DefaultGraphQLTagsProvider implements GraphQLTagsProvider {

	private final List<GraphQLTagsContributor> contributors;

	public DefaultGraphQLTagsProvider(List<GraphQLTagsContributor> contributors) {
		this.contributors = contributors;
	}


	@Override
	public Iterable<Tag> getExecutionTags(InstrumentationExecutionParameters parameters, ExecutionResult result, Throwable exception) {
		Tags tags = Tags.of(GraphQLTags.executionOutcome(result, exception));
		for (GraphQLTagsContributor contributor : this.contributors) {
			tags = tags.and(contributor.getExecutionTags(parameters, result, exception));
		}
		return tags;
	}

	@Override
	public Iterable<Tag> getErrorTags(InstrumentationExecutionParameters parameters, GraphQLError error) {
		Tags tags = Tags.of(GraphQLTags.errorType(error), GraphQLTags.errorPath(error));
		for (GraphQLTagsContributor contributor : this.contributors) {
			tags = tags.and(contributor.getErrorTags(parameters, error));
		}
		return tags;
	}

	@Override
	public Iterable<Tag> getDataFetchingTags(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, Throwable exception) {
		Tags tags = Tags.of(GraphQLTags.dataFetchingOutcome(exception), GraphQLTags.dataFetchingPath(parameters));
		for (GraphQLTagsContributor contributor : this.contributors) {
			tags = tags.and(contributor.getDataFetchingTags(dataFetcher, parameters, exception));
		}
		return tags;
	}
}

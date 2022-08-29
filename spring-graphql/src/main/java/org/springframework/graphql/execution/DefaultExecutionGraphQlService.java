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
import java.util.function.BiFunction;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.execution.ExecutionIdProvider;
import io.micrometer.context.ContextSnapshot;
import org.dataloader.DataLoaderRegistry;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;

/**
 * {@link ExecutionGraphQlService} that uses a {@link GraphQlSource} to obtain a
 * {@link GraphQL} instance and perform query execution.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class DefaultExecutionGraphQlService implements ExecutionGraphQlService {

	private static final BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> RESET_EXECUTION_ID_CONFIGURER =
			(executionInput, builder) -> builder.executionId(null).build();


	private final GraphQlSource graphQlSource;

	private final List<DataLoaderRegistrar> dataLoaderRegistrars = new ArrayList<>();

	private final boolean isDefaultExecutionIdProvider;


	public DefaultExecutionGraphQlService(GraphQlSource graphQlSource) {
		this.graphQlSource = graphQlSource;
		this.isDefaultExecutionIdProvider =
				(graphQlSource.graphQl().getIdProvider() == ExecutionIdProvider.DEFAULT_EXECUTION_ID_PROVIDER);
	}


	/**
	 * Add a registrar to get access to and configure the
	 * {@link DataLoaderRegistry} for each request.
	 * @param registrar the registrar to add
	 */
	public void addDataLoaderRegistrar(DataLoaderRegistrar registrar) {
		this.dataLoaderRegistrars.add(registrar);
	}


	@Override
	public final Mono<ExecutionGraphQlResponse> execute(ExecutionGraphQlRequest request) {
		return Mono.deferContextual((contextView) -> {
			if (!this.isDefaultExecutionIdProvider && request.getExecutionId() == null) {
				request.configureExecutionInput(RESET_EXECUTION_ID_CONFIGURER);
			}
			ExecutionInput executionInput = request.toExecutionInput();
			ContextSnapshot.captureFrom(contextView).updateContext(executionInput.getGraphQLContext());
			ExecutionInput updatedExecutionInput = registerDataLoaders(executionInput);
			return Mono.fromFuture(this.graphQlSource.graphQl().executeAsync(updatedExecutionInput))
					.map(result -> new DefaultExecutionGraphQlResponse(updatedExecutionInput, result));
		});
	}

	private ExecutionInput registerDataLoaders(ExecutionInput executionInput) {
		if (!this.dataLoaderRegistrars.isEmpty()) {
			GraphQLContext graphQLContext = executionInput.getGraphQLContext();
			DataLoaderRegistry previousRegistry = executionInput.getDataLoaderRegistry();
			DataLoaderRegistry newRegistry = DataLoaderRegistry.newRegistry().registerAll(previousRegistry).build();
			this.dataLoaderRegistrars.forEach(registrar -> registrar.registerDataLoaders(newRegistry, graphQLContext));
			executionInput = executionInput.transform(builder -> builder.dataLoaderRegistry(newRegistry));
		}
		return executionInput;
	}

}

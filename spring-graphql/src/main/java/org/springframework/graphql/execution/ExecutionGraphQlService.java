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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.dataloader.DataLoaderRegistry;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.RequestInput;

/**
 * {@link GraphQlService} that uses a {@link GraphQlSource} to obtain a
 * {@link GraphQL} instance and perform query execution.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ExecutionGraphQlService implements GraphQlService {

	private final GraphQlSource graphQlSource;

	private final List<DataLoaderRegistrar> dataLoaderRegistrars = new ArrayList<>();


	public ExecutionGraphQlService(GraphQlSource graphQlSource) {
		this.graphQlSource = graphQlSource;
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
	public final Mono<ExecutionResult> execute(RequestInput requestInput) {
		ExecutionInput executionInput = initExecutionInput(requestInput);
		GraphQL graphQl = this.graphQlSource.graphQl();

		return Mono.deferContextual((contextView) -> {
			ReactorContextManager.setReactorContext(contextView, executionInput);
			return Mono.fromFuture(graphQl.executeAsync(executionInput));
		});
	}

	private ExecutionInput initExecutionInput(RequestInput requestInput) {
		ExecutionInput input = requestInput.toExecutionInput();
		if (!this.dataLoaderRegistrars.isEmpty()) {
			DataLoaderRegistry previousRegistry = input.getDataLoaderRegistry();
			DataLoaderRegistry newRegistry = DataLoaderRegistry.newRegistry().registerAll(previousRegistry).build();
			this.dataLoaderRegistrars.forEach(registrar -> registrar.registerDataLoaders(newRegistry));
			input = input.transform(builder -> builder.dataLoaderRegistry(newRegistry));
		}
		return input;
	}

}

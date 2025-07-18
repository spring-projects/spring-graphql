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

package org.springframework.graphql.test.tester;


import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import graphql.ExecutionInput;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.util.Assert;


/**
 * {@code GraphQlTransport} that calls directly a {@link ExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 */
final class GraphQlServiceGraphQlTransport extends AbstractDirectGraphQlTransport {

	private final ExecutionGraphQlService graphQlService;

	private final List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers;


	GraphQlServiceGraphQlTransport(
			ExecutionGraphQlService graphQlService,
			List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers) {

		Assert.notNull(graphQlService, "GraphQlService is required");
		this.graphQlService = graphQlService;
		this.executionInputConfigurers = new ArrayList<>(executionInputConfigurers);
	}


	ExecutionGraphQlService getGraphQlService() {
		return this.graphQlService;
	}

	List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> getExecutionInputConfigurers() {
		return this.executionInputConfigurers;
	}

	@Override
	protected Mono<ExecutionGraphQlResponse> executeInternal(ExecutionGraphQlRequest request) {
		this.executionInputConfigurers.forEach(request::configureExecutionInput);
		return this.graphQlService.execute(request);
	}

}

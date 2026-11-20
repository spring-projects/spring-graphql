/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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

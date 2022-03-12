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

package org.springframework.graphql.test.tester;


import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.RequestInput;
import org.springframework.graphql.RequestOutput;
import org.springframework.util.Assert;


/**
 * {@code GraphQlTransport} that calls directly a {@link GraphQlService}.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
final class GraphQlServiceTransport extends AbstractDirectTransport {

	private final GraphQlService graphQlService;


	GraphQlServiceTransport(GraphQlService graphQlService) {
		Assert.notNull(graphQlService, "GraphQlService is required");
		this.graphQlService = graphQlService;
	}


	public GraphQlService getGraphQlService() {
		return this.graphQlService;
	}

	@Override
	protected Mono<RequestOutput> executeInternal(GraphQlRequest request) {

		RequestInput requestInput = new RequestInput(
				request.getDocument(), request.getOperationName(), request.getVariables(),
				idGenerator.generateId().toString(), null);

		return this.graphQlService.execute(requestInput);
	}

}

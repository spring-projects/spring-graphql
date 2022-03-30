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

package org.springframework.graphql.client;

import java.time.Duration;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.execution.MockExecutionGraphQlService;

/**
 * Base class for {@link GraphQlClient} tests.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlClientTestSupport {

	protected static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();

	private final GraphQlClient.Builder<?> clientBuilder = GraphQlClient.builder(this.graphQlService.asGraphQlTransport());

	private final GraphQlClient client = this.clientBuilder.build();


	protected GraphQlClient graphQlClient() {
		return this.client;
	}

	public GraphQlClient.Builder<?> graphQlClientBuilder() {
		return this.clientBuilder;
	}

	public MockExecutionGraphQlService getGraphQlService() {
		return this.graphQlService;
	}

	protected GraphQlRequest request() {
		return this.graphQlService.getGraphQlRequest();
	}

}

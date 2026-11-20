/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import java.time.Duration;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.execution.MockExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.util.Assert;

/**
 * Base class for {@link GraphQlClient} tests.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlClientTestSupport {

	protected static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();

	private final GraphQlClient.Builder<?> clientBuilder = GraphQlClient.builder(new MockTransport(this.graphQlService));

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


	private static class MockTransport implements GraphQlTransport {

		private final ExecutionGraphQlService graphQlService;

		private MockTransport(ExecutionGraphQlService graphQlService) {
			this.graphQlService = graphQlService;
		}

		@Override
		public Mono<GraphQlResponse> execute(GraphQlRequest request) {
			return graphQlService.execute(
							new DefaultExecutionGraphQlRequest(
									request.getDocument(),
									request.getOperationName(),
									request.getVariables(),
									request.getExtensions(),
									"1",
									null))
					.cast(GraphQlResponse.class);
		}

		@Override
		public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
			return graphQlService.execute(
							new DefaultExecutionGraphQlRequest(
									request.getDocument(),
									request.getOperationName(),
									request.getVariables(),
									request.getExtensions(),
									"1",
									null))
					.flatMapMany(response -> {
						Assert.notNull(response.getData(), "Response Data should not be null");
						Assert.state(response.getData() instanceof Publisher<?>, "Response Data should be a publisher");
						return response.getData();
					});
		}

	}

}

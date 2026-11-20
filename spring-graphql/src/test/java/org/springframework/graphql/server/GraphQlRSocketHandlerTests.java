/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.exceptions.RejectedException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.codec.Encoder;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.server.webflux.GraphQlWebSocketHandler;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.codec.json.JacksonJsonEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GraphQlWebSocketHandler}.
 * @author Rossen Stoyanchev
 */
class GraphQlRSocketHandlerTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final Encoder<?> encoder = new JacksonJsonEncoder();


	@Test
	void subscriptionWithFailedResponse() {

		String document = "subscription { greetings }";
		GraphQLError error = GraphqlErrorBuilder.newError().message("boo").build();
		ExecutionResult result = ExecutionResultImpl.newExecutionResult().addError(error).build();

		Flux<Map<String, Object>> responseFlux = handleSubscription(document, result);

		StepVerifier.create(responseFlux)
				.expectErrorSatisfies(ex -> {
					assertThat(ex).isInstanceOf(RejectedException.class);
					assertThat(ex.getMessage()).isEqualTo(
							"[{\"message\":\"boo\"," +
									"\"locations\":[]," +
									"\"errorType\":\"DataFetchingException\"," +
									"\"path\":null," +
									"\"extensions\":null}]");
				})
				.verify(TIMEOUT);
	}

	@Test
	void subscriptionWithValidResponseButNotPublisher() {

		String document = "subscription { greetings }";
		ExecutionResult result = ExecutionResultImpl.newExecutionResult().data(Collections.emptyMap()).build();

		Flux<Map<String, Object>> responseFlux = handleSubscription(document, result);

		StepVerifier.create(responseFlux)
				.expectErrorSatisfies(ex -> {
					assertThat(ex).isInstanceOf(InvalidException.class);
					assertThat(ex.getMessage()).startsWith("Expected a Publisher for a subscription operation.");
				})
				.verify(TIMEOUT);
	}

	private Flux<Map<String, Object>> handleSubscription(String document, ExecutionResult executionResult) {
		ExecutionGraphQlService service = stubService(document, executionResult);
		GraphQlRSocketHandler handler = new GraphQlRSocketHandler(service, Collections.emptyList(), this.encoder);
		return handler.handleSubscription(Collections.singletonMap("query", document));
	}

	private ExecutionGraphQlService stubService(String document, ExecutionResult result) {
		return request -> {
			assertThat(request.getDocument()).isEqualTo(document);
			ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(document).build();
			return Mono.just(new DefaultExecutionGraphQlResponse(executionInput, result));
		};
	}

}

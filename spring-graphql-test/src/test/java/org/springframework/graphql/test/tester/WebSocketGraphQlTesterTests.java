/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClientInterceptor;
import org.springframework.graphql.client.TestWebSocketClient;
import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.graphql.execution.MockExecutionGraphQlService;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.webflux.GraphQlWebSocketHandler;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebSocketGraphQlTester}.
 */
class WebSocketGraphQlTesterTests {


	@Test
	void shouldConfigureInterceptors() {
		TestInterceptor testInterceptor = new TestInterceptor();
		TestWebSocketClient webSocketClient = createWebSocketClient();
		WebSocketGraphQlClient client = WebSocketGraphQlClient.builder(URI.create(""), webSocketClient)
				.interceptor(testInterceptor)
				.build();

		client.document("{ Query }").execute().block(Duration.ofMillis(500));
		assertThat(testInterceptor.executed).as("Interceptor is not executed").isTrue();
	}

	private TestWebSocketClient createWebSocketClient() {
		MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();
		graphQlService.setDefaultResponse("{}");
		WebGraphQlHandler graphQlHandler = WebGraphQlHandler.builder(graphQlService).build();
		ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
		WebSocketHandler handler = GraphQlWebSocketHandler.builder(graphQlHandler, configurer, Duration.ofSeconds(5)).build();
		return new TestWebSocketClient(handler);
	}

	class TestInterceptor implements GraphQlClientInterceptor {

		AtomicBoolean executed = new AtomicBoolean();

		@Override
		public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
			return chain.next(request).doOnNext((response) -> executed.set(true));
		}
	}

}

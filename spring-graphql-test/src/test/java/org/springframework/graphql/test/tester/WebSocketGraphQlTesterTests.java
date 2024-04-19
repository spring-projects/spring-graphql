/*
 * Copyright 2020-2024 the original author or authors.
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
		WebSocketHandler handler = new GraphQlWebSocketHandler(graphQlHandler, configurer, Duration.ofSeconds(5));
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

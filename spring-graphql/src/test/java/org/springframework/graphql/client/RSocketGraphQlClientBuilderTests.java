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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.graphql.web.GraphQlRSocketHandler;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Rossen Stoyanchev
 */
public class RSocketGraphQlClientBuilderTests {

	private static final String DOCUMENT = "{ Query }";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);


	private final BuilderSetup builderSetup = new BuilderSetup();


	@Test
	void mutate() {

		// Original
		RSocketGraphQlClient.Builder<?> builder = this.builderSetup.initBuilder();
		RSocketGraphQlClient client = builder.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);

		GraphQlRequest request = this.builderSetup.getGraphQlRequest();
		assertThat(request).isNotNull();

		// Mutate
		client = client.mutate().build();
		client.document(DOCUMENT).execute().block(TIMEOUT);

		request = this.builderSetup.getGraphQlRequest();
		assertThat(request).isNotNull();
	}



	private static class BuilderSetup  {

		private GraphQlRequest graphQlRequest;

		private final Map<String, ExecutionGraphQlResponse> responses = new HashMap<>();

		public BuilderSetup() {

			ExecutionGraphQlResponse defaultResponse = new DefaultExecutionGraphQlResponse(
					ExecutionInput.newExecutionInput().query(DOCUMENT).build(),
					ExecutionResultImpl.newExecutionResult().build());

			this.responses.put(DOCUMENT, defaultResponse);
		}

		public RSocketGraphQlClient.Builder<?> initBuilder() {

			ExecutionGraphQlService graphQlService = request -> {
				this.graphQlRequest = request;
				String document = request.getDocument();
				ExecutionGraphQlResponse response = this.responses.get(document);
				Assert.notNull(response, "Unexpected request: " + document);
				return Mono.just(response);
			};

			GraphQlRSocketController controller = new GraphQlRSocketController(
					new GraphQlRSocketHandler(graphQlService, Collections.emptyList()));

			RSocketServer.create()
					.acceptor(createSocketAcceptor(controller))
					.bind(LocalServerTransport.create("local"))
					.block();

			return RSocketGraphQlClient.builder()
					.clientTransport(LocalClientTransport.create("local"));
		}

		private SocketAcceptor createSocketAcceptor(GraphQlRSocketController controller) {

			RSocketStrategies.Builder builder = RSocketStrategies.builder();
			builder.encoder(new Jackson2JsonEncoder());
			builder.decoder(new Jackson2JsonDecoder());

			RSocketMessageHandler handler = new RSocketMessageHandler();
			handler.setHandlers(Collections.singletonList(controller));
			handler.setRSocketStrategies(builder.build());
			handler.afterPropertiesSet();

			return handler.responder();
		}

		@SuppressWarnings("unused")
		public void setMockResponse(String document, ExecutionResult result) {
			ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(document).build();
			this.responses.put(document, new DefaultExecutionGraphQlResponse(executionInput, result));
		}

		public GraphQlRequest getGraphQlRequest() {
			return this.graphQlRequest;
		}
	}


	@Controller
	private static class GraphQlRSocketController {

		private final GraphQlRSocketHandler handler;

		GraphQlRSocketController(GraphQlRSocketHandler handler) {
			this.handler = handler;
		}

		@MessageMapping("graphql")
		public Mono<Map<String, Object>> handle(Map<String, Object> payload) {
			return this.handler.handle(payload);
		}

		@MessageMapping("graphql")
		public Flux<Map<String, Object>> handleSubscription(Map<String, Object> payload) {
			return this.handler.handleSubscription(payload);
		}

	}

}

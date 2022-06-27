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
import java.util.Map;

import io.rsocket.Closeable;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.execution.MockExecutionGraphQlService;
import org.springframework.graphql.server.GraphQlRSocketHandler;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.stereotype.Controller;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * {@code RSocketGraphQlClient} tests using {@link LocalClientTransport} and
 * {@link LocalServerTransport} for RSocket exchanges in memory, with stubbed
 * GraphQL responses.
 *
 * @author Rossen Stoyanchev
 */
public class RSocketGraphQlClientBuilderTests {

	private static final String DOCUMENT = "{ Query }";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);


	private final BuilderSetup builderSetup = new BuilderSetup();


	@AfterEach
	void tearDown() {
		this.builderSetup.shutDown();
	}

	@Test
	void mutate() {

		// Original
		RSocketGraphQlClient.Builder<?> builder = this.builderSetup.initBuilder();
		RSocketGraphQlClient client = builder.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);

		GraphQlRequest request = this.builderSetup.getActualRequest();
		assertThat(request).isNotNull();

		// Mutate: still works (carries over original default)
		client = client.mutate().build();
		client.document(DOCUMENT).execute().block(TIMEOUT);

		request = this.builderSetup.getActualRequest();
		assertThat(request).isNotNull();
	}


	private static class BuilderSetup  {

		private final MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();

		@Nullable
		private Closeable server;

		public BuilderSetup() {
			this.graphQlService.setDefaultResponse("{}");
		}

		public MockExecutionGraphQlService getGraphQlService() {
			return this.graphQlService;
		}

		public GraphQlRequest getActualRequest() {
			return this.graphQlService.getGraphQlRequest();
		}

		public RSocketGraphQlClient.Builder<?> initBuilder() {

			GraphQlRSocketController controller = new GraphQlRSocketController(
					new GraphQlRSocketHandler(this.graphQlService, Collections.emptyList(), new Jackson2JsonEncoder()));

			this.server = RSocketServer.create()
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

		public void shutDown() {
			if (this.server != null) {
				this.server.dispose();
			}
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

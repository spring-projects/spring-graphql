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

import java.util.Collections;
import java.util.Map;

import graphql.ExecutionResultImpl;
import io.rsocket.Closeable;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.local.LocalClientTransport;
import io.rsocket.transport.local.LocalServerTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
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
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Rossen Stoyanchev
 */
public class RSocketGraphQlTesterBuilderTests {

	private static final String DOCUMENT = "{ Query }";


	private final BuilderSetup builderSetup = new BuilderSetup();


	@AfterEach
	void tearDown() {
		this.builderSetup.shutDown();
	}


	@Test
	void mutate() {

		// Original
		RSocketGraphQlTester.Builder<?> builder = this.builderSetup.initBuilder();
		RSocketGraphQlTester tester = builder.build();
		tester.document(DOCUMENT).executeAndVerify();

		GraphQlRequest request = this.builderSetup.getGraphQlRequest();
		assertThat(request).isNotNull();

		// Mutate: still works (carries over original default)
		tester = tester.mutate().build();
		tester.document(DOCUMENT).executeAndVerify();

		request = this.builderSetup.getGraphQlRequest();
		assertThat(request).isNotNull();
	}

	@Test
	void rsocketStrategiesRegistersJsonPathMappingProvider() {

		TestJackson2JsonDecoder testDecoder = new TestJackson2JsonDecoder();

		RSocketGraphQlTester.Builder<?> builder = this.builderSetup.initBuilder()
				.rsocketRequester(requesterBuilder -> {
					RSocketStrategies strategies = RSocketStrategies.builder()
							.encoder(new Jackson2JsonEncoder())
							.decoder(testDecoder)
							.build();
					requesterBuilder.rsocketStrategies(strategies);
				});

		String document = "{me {name}}";
		MovieCharacter character = MovieCharacter.create("Luke Skywalker");
		this.builderSetup.getGraphQlService().setResponse(document,
				ExecutionResultImpl.newExecutionResult()
						.data(Collections.singletonMap("me", character))
						.build());

		RSocketGraphQlTester client = builder.build();
		GraphQlTester.Response response = client.document(document).execute();

		testDecoder.resetLastValue();
		assertThat(testDecoder.getLastValue()).isNull();

		assertThat(response).isNotNull();
		response.path("me").entity(MovieCharacter.class).isEqualTo(character);
		response.path("me").matchesJson("{name:\"Luke Skywalker\"}");
		assertThat(testDecoder.getLastValue()).isEqualTo(character);
	}

	
	private static class BuilderSetup  {

		private final MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();

		@Nullable
		private Closeable server;

		public BuilderSetup() {
			this.graphQlService.setDefaultResponse("{}");
		}

		public RSocketGraphQlTester.Builder<?> initBuilder() {

			GraphQlRSocketController controller = new GraphQlRSocketController(
					new GraphQlRSocketHandler(this.graphQlService, Collections.emptyList(), new Jackson2JsonEncoder()));

			this.server = RSocketServer.create()
					.acceptor(createSocketAcceptor(controller))
					.bind(LocalServerTransport.create("local"))
					.block();

			return RSocketGraphQlTester.builder()
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

		public MockExecutionGraphQlService getGraphQlService() {
			return this.graphQlService;
		}

		public GraphQlRequest getGraphQlRequest() {
			return this.graphQlService.getGraphQlRequest();
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


	private static class TestJackson2JsonDecoder extends Jackson2JsonDecoder {

		@Nullable
		private Object lastValue;

		@Nullable
		Object getLastValue() {
			return this.lastValue;
		}

		@Override
		public Object decode(DataBuffer dataBuffer, ResolvableType targetType,
				@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {

			this.lastValue = super.decode(dataBuffer, targetType, mimeType, hints);
			return this.lastValue;
		}

		void resetLastValue() {
			this.lastValue = null;
		}

	}

}

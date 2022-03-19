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

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.graphql.RequestOutput;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.graphql.web.TestWebSocketClient;
import org.springframework.graphql.web.TestWebSocketConnection;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebInterceptor;
import org.springframework.graphql.web.webflux.GraphQlHttpHandler;
import org.springframework.graphql.web.webflux.GraphQlWebSocketHandler;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.HttpHandlerConnector;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Tests for the builders of Web {@code GraphQlClient} extensions, using a
 * {@link WebInterceptor} to capture the WebInput on the server side, and
 * optionally returning a mock response, or an empty response.
 *
 * <ul>
 * <li>{@link HttpGraphQlClient} via {@link HttpHandlerConnector} to {@link GraphQlHttpHandler}
 * <li>{@link WebSocketGraphQlClient} via a {@link TestWebSocketConnection} to {@link GraphQlWebSocketHandler}
 * </ul>
 *
 * @author Rossen Stoyanchev
 */
public class WebGraphQlClientBuilderTests {

	private static final String DOCUMENT = "{ Query }";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);


	public static Stream<ClientBuilderSetup> argumentSource() {
		return Stream.of(new HttpBuilderSetup(), new WebSocketBuilderSetup());
	}


	@ParameterizedTest
	@MethodSource("argumentSource")
	void mutateUrlHeaders(ClientBuilderSetup builderSetup) {

		String url = "/graphql-one";

		// Original
		WebGraphQlClient.Builder<?> builder = builderSetup.initBuilder()
				.url(url)
				.headers(headers -> headers.add("h", "one"));

		WebGraphQlClient client = builder.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);

		WebInput input = builderSetup.getWebInput();
		assertThat(input.getUri().toString()).isEqualTo(url);
		assertThat(input.getHeaders().get("h")).containsExactly("one");

		// Mutate to add header value
		builder = client.mutate().headers(headers -> headers.add("h", "two"));
		client = builder.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);
		assertThat(builderSetup.getWebInput().getHeaders().get("h")).containsExactly("one", "two");

		// Mutate to replace header
		builder = client.mutate().header("h", "three", "four");
		client = builder.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);

		input = builderSetup.getWebInput();
		assertThat(input.getUri().toString()).isEqualTo(url);
		assertThat(input.getHeaders().get("h")).containsExactly("three", "four");
	}

	@Test
	void mutateWebTestClientViaConsumer() {
		HttpBuilderSetup clientSetup = new HttpBuilderSetup();

		// Original header value
		HttpGraphQlClient.Builder<?> builder = clientSetup.initBuilder()
				.webClient(testClientBuilder -> testClientBuilder.defaultHeaders(h -> h.add("h", "one")));

		HttpGraphQlClient client = builder.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);
		assertThat(clientSetup.getWebInput().getHeaders().get("h")).containsExactly("one");

		// Mutate to add header value
		HttpGraphQlClient.Builder<?> builder2 = client.mutate()
				.webClient(testClientBuilder -> testClientBuilder.defaultHeaders(h -> h.add("h", "two")));

		client = builder2.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);
		assertThat(clientSetup.getWebInput().getHeaders().get("h")).containsExactly("one", "two");

		// Mutate to replace header
		HttpGraphQlClient.Builder<?> builder3 = client.mutate()
				.webClient(testClientBuilder -> testClientBuilder.defaultHeader("h", "three"));

		client = builder3.build();
		client.document(DOCUMENT).execute().block(TIMEOUT);
		assertThat(clientSetup.getWebInput().getHeaders().get("h")).containsExactly("three");
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void mutateDocumentSource(ClientBuilderSetup builderSetup) {

		DocumentSource documentSource = name -> name.equals("name") ?
				Mono.just(DOCUMENT) : Mono.error(new IllegalArgumentException());

		// Original
		WebGraphQlClient.Builder<?> builder = builderSetup.initBuilder().documentSource(documentSource);
		WebGraphQlClient client = builder.build();
		client.documentName("name").execute().block(TIMEOUT);

		WebInput input = builderSetup.getWebInput();
		assertThat(input.getDocument()).isEqualTo(DOCUMENT);

		// Mutate
		client = client.mutate().build();
		client.documentName("name").execute().block(TIMEOUT);

		input = builderSetup.getWebInput();
		assertThat(input.getDocument()).isEqualTo(DOCUMENT);
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void urlEncoding(ClientBuilderSetup builderSetup) {

		WebGraphQlClient client = builderSetup.initBuilder().url("/graphql one").build();
		client.document(DOCUMENT).execute().block(TIMEOUT);

		assertThat(builderSetup.getWebInput().getUri().toString()).isEqualTo("/graphql%20one");
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void codecConfigurerRegistersJsonPathMappingProvider(ClientBuilderSetup builderSetup) {

		TestJackson2JsonDecoder testDecoder = new TestJackson2JsonDecoder();

		WebGraphQlClient.Builder<?> builder = builderSetup.initBuilder()
				.codecConfigurer(codecConfigurer -> codecConfigurer.customCodecs().register(testDecoder));

		String document = "{me {name}}";
		MovieCharacter character = MovieCharacter.create("Luke Skywalker");
		builderSetup.setMockResponse(document,
				ExecutionResultImpl.newExecutionResult()
						.data(Collections.singletonMap("me", character))
						.build());

		WebGraphQlClient client = builder.build();
		ClientGraphQlResponse response = client.document(document).execute().block(TIMEOUT);

		testDecoder.resetLastValue();
		assertThat(testDecoder.getLastValue()).isNull();

		assertThat(response).isNotNull();
		assertThat(response.field("me").toEntity(MovieCharacter.class).getName()).isEqualTo("Luke Skywalker");
		assertThat(testDecoder.getLastValue()).isEqualTo(character);
	}


	private interface ClientBuilderSetup {

		WebGraphQlClient.Builder<?> initBuilder();

		void setMockResponse(String document, ExecutionResult result);

		WebInput getWebInput();

	}


	private abstract static class AbstractBuilderSetup implements ClientBuilderSetup {

		private WebInput webInput;

		private final Map<String, RequestOutput> responses = new HashMap<>();

		public AbstractBuilderSetup() {

			RequestOutput defaultResponse = new RequestOutput(
					ExecutionInput.newExecutionInput().query(DOCUMENT).build(),
					ExecutionResultImpl.newExecutionResult().build());

			this.responses.put(DOCUMENT, defaultResponse);
		}

		protected WebGraphQlHandler webGraphQlHandler() {
			return WebGraphQlHandler.builder(requestInput -> {
						String document = requestInput.getDocument();
						RequestOutput output = this.responses.get(document);
						Assert.notNull(output, "Unexpected request: " + document);
						return Mono.just(output);
					})
					.interceptor((input, chain) -> {
						this.webInput = input;
						return chain.next(webInput);
					})
					.build();
		}

		@Override
		public void setMockResponse(String document, ExecutionResult result) {
			ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(document).build();
			this.responses.put(document, new RequestOutput(executionInput, result));
		}

		@Override
		public WebInput getWebInput() {
			return this.webInput;
		}

	}


	private static class HttpBuilderSetup extends AbstractBuilderSetup {

		@Override
		public HttpGraphQlClient.Builder<?> initBuilder() {
			GraphQlHttpHandler handler = new GraphQlHttpHandler(webGraphQlHandler());
			RouterFunction<ServerResponse> routerFunction = route().POST("/**", handler::handleRequest).build();
			HttpHandler httpHandler = RouterFunctions.toHttpHandler(routerFunction, HandlerStrategies.withDefaults());
			HttpHandlerConnector connector = new HttpHandlerConnector(httpHandler);
			return HttpGraphQlClient.builder(WebClient.builder().clientConnector(connector));
		}

	}


	private static class WebSocketBuilderSetup extends AbstractBuilderSetup {

		@Override
		public WebSocketGraphQlClient.Builder<?> initBuilder() {
			ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
			WebSocketHandler handler = new GraphQlWebSocketHandler(webGraphQlHandler(), configurer, Duration.ofSeconds(5));
			return WebSocketGraphQlClient.builder(URI.create(""), new TestWebSocketClient(handler));
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

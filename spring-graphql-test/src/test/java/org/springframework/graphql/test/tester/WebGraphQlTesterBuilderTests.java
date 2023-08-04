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

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.graphql.client.TestWebSocketClient;
import org.springframework.graphql.client.TestWebSocketConnection;
import org.springframework.graphql.execution.MockExecutionGraphQlService;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.webflux.GraphQlHttpHandler;
import org.springframework.graphql.server.webflux.GraphQlWebSocketHandler;
import org.springframework.graphql.support.DocumentSource;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;


/**
 * Tests for the builders of Web {@code GraphQlTester} extensions, using a
 * {@link WebGraphQlInterceptor} to capture the WebGraphQlRequest on the
 * server side, and optionally returning a mock response, or an empty response.
 *
 * <ul>
 * <li>{@link HttpGraphQlTester} via {@link WebTestClient} to {@link GraphQlHttpHandler}
 * <li>{@link WebSocketGraphQlTester} via a {@link TestWebSocketConnection} to {@link GraphQlWebSocketHandler}
 * <li>{@link WebGraphQlTester} via direct call to {@link WebGraphQlHandler}
 * </ul>
 *
 * @author Rossen Stoyanchev
 */
public class WebGraphQlTesterBuilderTests {

	private static final String DOCUMENT = "{ Query }";


	public static Stream<TesterBuilderSetup> argumentSource() {
		return Stream.of(new WebBuilderSetup(), new HttpBuilderSetup(), new WebSocketBuilderSetup());
	}


	@ParameterizedTest
	@MethodSource("argumentSource")
	void mutateUrlHeaders(TesterBuilderSetup builderSetup) {

		String url = "/graphql-one";

		// Original
		WebGraphQlTester.Builder<?> builder = builderSetup.initBuilder()
				.url(url)
				.headers(headers -> headers.add("h", "one"));

		WebGraphQlTester tester = builder.build();
		tester.document(DOCUMENT).execute();

		WebGraphQlRequest request = builderSetup.getWebGraphQlRequest();
		assertThat(request.getUri().toString()).isEqualTo(url);
		assertThat(request.getHeaders().get("h")).containsExactly("one");

		// Mutate to add header value
		builder = tester.mutate().headers(headers -> headers.add("h", "two"));
		tester = builder.build();
		tester.document(DOCUMENT).execute();
		assertThat(builderSetup.getWebGraphQlRequest().getHeaders().get("h")).containsExactly("one", "two");

		// Mutate to replace header
		builder = tester.mutate().header("h", "three", "four");
		tester = builder.build();
		tester.document(DOCUMENT).execute();

		request = builderSetup.getWebGraphQlRequest();
		assertThat(request.getUri().toString()).isEqualTo(url);
		assertThat(request.getHeaders().get("h")).containsExactly("three", "four");
	}

	@Test
	void mutateWebTestClientViaConsumer() {
		HttpBuilderSetup testerSetup = new HttpBuilderSetup();

		// Original header value
		HttpGraphQlTester.Builder<?> builder = testerSetup.initBuilder()
				.webTestClient(testClientBuilder -> testClientBuilder.defaultHeaders(h -> h.add("h", "one")));

		HttpGraphQlTester tester = builder.build();
		tester.document(DOCUMENT).execute();
		assertThat(testerSetup.getWebGraphQlRequest().getHeaders().get("h")).containsExactly("one");

		// Mutate to add header value
		HttpGraphQlTester.Builder<?> builder2 = tester.mutate()
				.webTestClient(testClientBuilder -> testClientBuilder.defaultHeaders(h -> h.add("h", "two")));

		tester = builder2.build();
		tester.document(DOCUMENT).execute();
		assertThat(testerSetup.getWebGraphQlRequest().getHeaders().get("h")).containsExactly("one", "two");

		// Mutate to replace header
		HttpGraphQlTester.Builder<?> builder3 = tester.mutate()
				.webTestClient(testClientBuilder -> testClientBuilder.defaultHeader("h", "three"));

		tester = builder3.build();
		tester.document(DOCUMENT).execute();
		assertThat(testerSetup.getWebGraphQlRequest().getHeaders().get("h")).containsExactly("three");
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void mutateDocumentSource(TesterBuilderSetup builderSetup) {

		DocumentSource documentSource = name -> "name".equals(name) ?
				Mono.just(DOCUMENT) : Mono.error(new IllegalArgumentException());

		// Original
		WebGraphQlTester.Builder<?> builder = builderSetup.initBuilder().documentSource(documentSource);
		WebGraphQlTester tester = builder.build();
		tester.documentName("name").execute();

		WebGraphQlRequest input = builderSetup.getWebGraphQlRequest();
		assertThat(input.getDocument()).isEqualTo(DOCUMENT);

		// Mutate
		tester = tester.mutate().build();
		tester.documentName("name").execute();

		input = builderSetup.getWebGraphQlRequest();
		assertThat(input.getDocument()).isEqualTo(DOCUMENT);
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void codecConfigurerRegistersJsonPathMappingProvider(TesterBuilderSetup builderSetup) {

		TestJackson2JsonDecoder testDecoder = new TestJackson2JsonDecoder();

		WebGraphQlTester.Builder<?> builder = builderSetup.initBuilder()
				.codecConfigurer(codecConfigurer -> codecConfigurer.customCodecs().register(testDecoder));

		String document = "{me {name}}";
		MovieCharacter character = MovieCharacter.create("Luke Skywalker");
		builderSetup.setMockResponse(document,
				ExecutionResultImpl.newExecutionResult()
						.data(Collections.singletonMap("me", character))
						.build());

		WebGraphQlTester client = builder.build();
		GraphQlTester.Response response = client.document(document).execute();

		testDecoder.resetLastValue();
		assertThat(testDecoder.getLastValue()).isNull();

		assertThat(response).isNotNull();
		response.path("me").entity(MovieCharacter.class).isEqualTo(character);
		response.path("me").matchesJson("{name:\"Luke Skywalker\"}");
		assertThat(testDecoder.getLastValue()).isEqualTo(character);
	}


	private interface TesterBuilderSetup {

		WebGraphQlTester.Builder<?> initBuilder();

		void setMockResponse(String document, ExecutionResult result);

		WebGraphQlRequest getWebGraphQlRequest();

	}


	private static class WebBuilderSetup implements TesterBuilderSetup {

		@Nullable
		private WebGraphQlRequest request;

		private final MockExecutionGraphQlService graphQlService = new MockExecutionGraphQlService();

		public WebBuilderSetup() {
			this.graphQlService.setDefaultResponse("{}");
		}

		@Override
		public WebGraphQlTester.Builder<?> initBuilder() {
			return WebGraphQlTester.builder(webGraphQlHandler());
		}

		protected WebGraphQlHandler webGraphQlHandler() {
			return WebGraphQlHandler.builder(this.graphQlService)
					.interceptor((input, chain) -> {
						this.request = input;
						return chain.next(request);
					})
					.build();
		}

		@Override
		public void setMockResponse(String document, ExecutionResult result) {
			this.graphQlService.setResponse(document, result);
		}

		@Override
		public WebGraphQlRequest getWebGraphQlRequest() {
			Assert.state(this.request != null, "No saved WebGraphQlRequest");
			return this.request;
		}

	}


	private static class HttpBuilderSetup extends WebBuilderSetup {

		@Override
		public HttpGraphQlTester.Builder<?> initBuilder() {
			GraphQlHttpHandler handler = new GraphQlHttpHandler(webGraphQlHandler());
			RouterFunction<ServerResponse> routerFunction = route().POST("/**", handler::handleRequest).build();
			return HttpGraphQlTester.builder(WebTestClient.bindToRouterFunction(routerFunction).configureClient());
		}

	}


	private static class WebSocketBuilderSetup extends WebBuilderSetup {

		@Override
		public WebSocketGraphQlTester.Builder<?> initBuilder() {
			ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
			WebSocketHandler handler = new GraphQlWebSocketHandler(webGraphQlHandler(), configurer, Duration.ofSeconds(5));
			return WebSocketGraphQlTester.builder(URI.create(""), new TestWebSocketClient(handler));
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

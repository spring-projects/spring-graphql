/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.graphql.server.webflux;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.core.codec.DataBufferEncoder;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlHttpHandler}.
 * @author Rossen Stoyanchev
 */
class GraphQlHttpHandlerTests {

	private static final List<HttpMessageReader<?>> MESSAGE_READERS =
			List.of(new DecoderHttpMessageReader<>(new Jackson2JsonDecoder()));

	private final GraphQlHttpHandler greetingHandler =
			GraphQlSetup.schemaContent("type Query { greeting: String }")
					.queryFetcher("greeting", (env) -> "Hello")
					.toHttpHandlerWebFlux();


	@Test
	void shouldProduceApplicationJsonByDefault() throws Exception {
		String document = "{greeting}";
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.ALL)
				.body(initRequestBody(document));

		MockServerHttpResponse response = handleRequest(httpRequest, this.greetingHandler);

		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		StepVerifier.create(response.getBodyAsString())
				.expectNext("{\"data\":{\"greeting\":\"Hello\"}}")
				.verifyComplete();
	}

	@Test
	void shouldSupportApplicationGraphQl() throws Exception {
		String document = "{greeting}";
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.parseMediaType("application/graphql"))
				.accept(MediaType.ALL)
				.body(initRequestBody(document));

		MockServerHttpResponse response = handleRequest(httpRequest, this.greetingHandler);

		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		StepVerifier.create(response.getBodyAsString())
				.expectNext("{\"data\":{\"greeting\":\"Hello\"}}")
				.verifyComplete();
	}

	@Test
	void shouldSupportApplicationGraphQlWithCharset() throws Exception {
		String document = "{greeting}";
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.parseMediaType("application/graphql;charset=UTF-8"))
				.accept(MediaType.ALL)
				.body(initRequestBody(document));

		MockServerHttpResponse response = handleRequest(httpRequest, this.greetingHandler);

		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		StepVerifier.create(response.getBodyAsString())
				.expectNext("{\"data\":{\"greeting\":\"Hello\"}}")
				.verifyComplete();
	}

	@Test
	void shouldProduceApplicationGraphQl() throws Exception {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.body(initRequestBody("{greeting}"));

		MockServerHttpResponse httpResponse = handleRequest(httpRequest, this.greetingHandler);

		assertThat(httpResponse.getHeaders().getContentType()).isEqualTo(MediaTypes.APPLICATION_GRAPHQL_RESPONSE);
	}

	@Test
	void shouldProduceApplicationJson() throws Exception {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(initRequestBody("{greeting}"));

		MockServerHttpResponse httpResponse = handleRequest(httpRequest, this.greetingHandler);

		assertThat(httpResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
	}

	@Test
	void locale() throws Exception {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "Hello in " + env.getLocale())
				.toHttpHandlerWebFlux();

		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.acceptLanguageAsLocales(Locale.FRENCH)
				.body(initRequestBody("{greeting}"));

		MockServerHttpResponse httpResponse = handleRequest(httpRequest, handler);

		assertThat(httpResponse.getBodyAsString().block())
				.isEqualTo("{\"data\":{\"greeting\":\"Hello in fr\"}}");
	}

	@Test
	void shouldSetExecutionId() throws Exception {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { showId: String }")
				.queryFetcher("showId", (env) -> env.getExecutionId().toString())
				.toHttpHandlerWebFlux();

		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.body(initRequestBody("{showId}"));

		MockServerHttpResponse httpResponse = handleRequest(httpRequest, handler);

		DocumentContext document = JsonPath.parse(httpResponse.getBodyAsString().block());
		String id = document.read("data.showId", String.class);
		assertThat(id).isEqualTo(httpRequest.getId());
	}

	@Test
	void shouldUseCustomCodec() {
		WebGraphQlHandler webGraphQlHandler = GraphQlSetup.schemaContent("type Query { showId: String }")
				.queryFetcher("showId", (env) -> env.getExecutionId().toString())
				.toWebGraphQlHandler();

		ObjectMapper mapper = new ObjectMapper();
		CodecConfigurer configurer = ServerCodecConfigurer.create();
		configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
		configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));

		byte[] bytes = "{\"query\": \"{showId}\"}".getBytes(StandardCharsets.UTF_8);
		Flux<DefaultDataBuffer> body = Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes));

		MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
				.body(body);

		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
		ServerRequest request = ServerRequest.create(exchange, configurer.getReaders());

		new GraphQlHttpHandler(webGraphQlHandler, configurer)
				.handleRequest(request)
				.flatMap(response -> response.writeTo(exchange, new EmptyContext()))
				.block();

		DocumentContext document = JsonPath.parse(exchange.getResponse().getBodyAsString().block());
		String id = document.read("data.showId", String.class);
		assertThat(id).isEqualTo(httpRequest.getId());
	}

	private static String initRequestBody(String document) throws Exception {
		SerializableGraphQlRequest request = new SerializableGraphQlRequest();
		request.setQuery(document);
		return new ObjectMapper().writeValueAsString(request);
	}

	private MockServerHttpResponse handleRequest(MockServerHttpRequest httpRequest, GraphQlHttpHandler handler) {
		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
		ServerRequest serverRequest = ServerRequest.create(exchange, MESSAGE_READERS);

		handler.handleRequest(serverRequest)
				.flatMap(response -> response.writeTo(exchange, new DefaultContext()))
				.block();

		return exchange.getResponse();
	}


	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return Collections.singletonList(new EncoderHttpMessageWriter<>(new Jackson2JsonEncoder()));
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return Collections.emptyList();
		}

	}

	private static class EmptyContext implements ServerResponse.Context {
		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return List.of(new EncoderHttpMessageWriter<>(new DataBufferEncoder()));
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return List.of();
		}
	}

}

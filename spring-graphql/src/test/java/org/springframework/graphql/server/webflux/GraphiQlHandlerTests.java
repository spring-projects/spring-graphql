/*
 * Copyright 2020-2022 the original author or authors.
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
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.core.codec.ResourceEncoder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphiQlHandler}.
 * @author Brian Clozel
 */
class GraphiQlHandlerTests {

	private static final List<HttpMessageReader<?>> MESSAGE_READERS = Collections.emptyList();

	private final GraphiQlHandler handler = initHandler("/graphql");


	private static GraphiQlHandler initHandler(String path) {
		return new GraphiQlHandler(path, null, new ByteArrayResource("GRAPHIQL".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void shouldRedirectWithPathQueryParameter() {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("/graphiql").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
		ServerRequest request = ServerRequest.create(exchange, MESSAGE_READERS);
		ServerResponse response = this.handler.handleRequest(request).block();
		assertThat(response.statusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
		assertThat(response.headers().getLocation()).isNotNull();
		assertThat(response.headers().getLocation().toASCIIString()).isEqualTo("/graphiql?path=/graphql");
	}

	@Test
	void shouldRedirectWithPathAndWsPathQueryParameter() {
		GraphiQlHandler wsHandler = new GraphiQlHandler("/graphql", "/graphql",
				new ByteArrayResource("GRAPHIQL".getBytes(StandardCharsets.UTF_8)));
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("/graphiql").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
		ServerRequest request = ServerRequest.create(exchange, MESSAGE_READERS);
		ServerResponse response = wsHandler.handleRequest(request).block();
		assertThat(response.statusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
		assertThat(response.headers().getLocation()).isNotNull();
		assertThat(response.headers().getLocation().toASCIIString()).isEqualTo("/graphiql?path=/graphql&wsPath=/graphql");
	}

	@Test // gh-478
	void shouldRedirectWithPathVariables() {
		Map<String, Object> pathVariables = Collections.singletonMap("envId", "123");
		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString("/env/{envId}/graphiql");
		String path = uriBuilder.build(pathVariables).toString();

		MockServerHttpRequest httpRequest = MockServerHttpRequest.get(path).build();
		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
		exchange.getAttributes().put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
		ServerRequest request = ServerRequest.create(exchange, MESSAGE_READERS);

		GraphiQlHandler graphiQlHandler = initHandler(uriBuilder.build().toString());
		ServerResponse response = graphiQlHandler.handleRequest(request).block();

		assertThat(response.statusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
		assertThat(response.headers().getLocation()).isNotNull();
		assertThat(response.headers().getLocation().toASCIIString()).isEqualTo(path + "?path=" + path);
	}

	@Test
	void shouldServeGraphiQlHtmlResource() {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("/graphiql").queryParam("path", "/graphql").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
		ServerRequest request = ServerRequest.create(exchange, MESSAGE_READERS);
		ServerResponse response = this.handler.handleRequest(request).block();
		assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.headers().getContentType()).isEqualTo(MediaType.TEXT_HTML);
		assertThat(getResponseContent(exchange, response)).isEqualTo("GRAPHIQL");
	}

	@Test
	void shouldConsiderContextPathWhenRedirecting() {
		MockServerHttpRequest httpRequest = MockServerHttpRequest.get("/context/graphiql").contextPath("/context").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
		ServerRequest request = ServerRequest.create(exchange, MESSAGE_READERS);
		assertThat(request.requestPath().contextPath().toString()).isEqualTo("/context");
		assertThat(request.requestPath().pathWithinApplication().toString()).isEqualTo("/graphiql");

		ServerResponse response = this.handler.handleRequest(request).block();
		assertThat(response.statusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
		assertThat(response.headers().getLocation()).isNotNull();
		assertThat(response.headers().getLocation().toASCIIString()).isEqualTo("/context/graphiql?path=/context/graphql");
	}

	private String getResponseContent(MockServerWebExchange exchange, ServerResponse response) {
		response.writeTo(exchange, new DefaultContext()).block();
		return exchange.getResponse().getBodyAsString().block();
	}

	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return Collections.singletonList(new EncoderHttpMessageWriter<>(new ResourceEncoder()));
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return Collections.emptyList();
		}

	}

}
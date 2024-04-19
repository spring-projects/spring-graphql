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

package org.springframework.graphql.server.webmvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.execution.preparsed.persisted.ApolloPersistedQuerySupport;
import graphql.execution.preparsed.persisted.InMemoryPersistedQueryCache;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link GraphQlHttpHandler}.
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
public class GraphQlHttpHandlerTests {

	private static final List<HttpMessageConverter<?>> MESSAGE_READERS =
			List.of(new MappingJackson2HttpMessageConverter(), new ByteArrayHttpMessageConverter());

	private final GraphQlHttpHandler greetingHandler = GraphQlSetup.schemaContent("type Query { greeting: String }")
			.queryFetcher("greeting", (env) -> "Hello").toHttpHandler();


	@Test
	void shouldProduceApplicationJsonByDefault() throws Exception {
		MockHttpServletRequest request = createServletRequest("{ greeting }", "*/*");
		MockHttpServletResponse response = handleRequest(request, this.greetingHandler);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getContentAsString()).isEqualTo("{\"data\":{\"greeting\":\"Hello\"}}");
	}

	@Test
	void shouldSupportApplicationGraphQl() throws Exception {
		MockHttpServletRequest request = createServletRequest("{ greeting }", "*/*");
		request.setContentType("application/graphql");

		MockHttpServletResponse response = handleRequest(request, this.greetingHandler);
		assertThat(response.getContentAsString()).isEqualTo("{\"data\":{\"greeting\":\"Hello\"}}");
	}

	@Test
	void shouldProduceApplicationGraphQl() throws Exception {
		MockHttpServletRequest request = createServletRequest("{ greeting }", MediaType.APPLICATION_GRAPHQL_RESPONSE_VALUE);
		MockHttpServletResponse response = handleRequest(request, this.greetingHandler);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_GRAPHQL_RESPONSE_VALUE);
	}

	@Test
	void shouldProduceApplicationJson() throws Exception {
		MockHttpServletRequest servletRequest = createServletRequest("{ greeting }", "application/json");
		MockHttpServletResponse servletResponse = handleRequest(servletRequest, this.greetingHandler);
		assertThat(servletResponse.getContentType()).isEqualTo("application/json");
	}

	@Test
	void locale() throws Exception {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "Hello in " + env.getLocale())
				.toHttpHandler();

		MockHttpServletRequest request = createServletRequest(
				"{ greeting }", MediaType.APPLICATION_GRAPHQL_RESPONSE_VALUE);

		LocaleContextHolder.setLocale(Locale.FRENCH);

		try {
			MockHttpServletResponse response = handleRequest(request, handler);
			assertThat(response.getContentAsString()).isEqualTo("{\"data\":{\"greeting\":\"Hello in fr\"}}");
		}
		finally {
			LocaleContextHolder.resetLocaleContext();
		}
	}

	@Test
	void shouldSetExecutionId() throws Exception {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { showId: ID! }")
				.queryFetcher("showId", (env) -> env.getExecutionId().toString())
				.toHttpHandler();

		MockHttpServletRequest request = createServletRequest(
				"{ showId }", MediaType.APPLICATION_GRAPHQL_RESPONSE_VALUE);

		MockHttpServletResponse response = handleRequest(request, handler);

		DocumentContext document = JsonPath.parse(response.getContentAsString());
		String id = document.read("data.showId", String.class);
		assertThatNoException().isThrownBy(() -> UUID.fromString(id));
	}

	@Test
	void persistedQuery() throws Exception {

		ApolloPersistedQuerySupport documentProvider =
				new ApolloPersistedQuerySupport(new InMemoryPersistedQueryCache(Collections.emptyMap()));

		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.configureGraphQl(builder -> builder.preparsedDocumentProvider(documentProvider))
				.toHttpHandler();

		SerializableGraphQlRequest request = new SerializableGraphQlRequest();
		request.setQuery("{__typename}");
		request.setExtensions(Map.of("persistedQuery", Map.of(
				"version", "1",
				"sha256Hash", "ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38")));

		MockHttpServletResponse response = handleRequest(createServletRequest(request, "*/*"), handler);
		assertThat(response.getContentAsString()).isEqualTo("{\"data\":{\"__typename\":\"Query\"}}");

		request = new SerializableGraphQlRequest();
		request.setQuery("{__typename}");
		request.setExtensions(Map.of("persistedQuery", Map.of(
				"version", "1",
				"sha256Hash", "ecf4edb46db40b5132295c0291d62fb65d6759a9eedfa4d5d612dd5ec54a6b38")));

		response = handleRequest(createServletRequest(request, "*/*"), handler);
		assertThat(response.getContentAsString()).isEqualTo("{\"data\":{\"__typename\":\"Query\"}}");
	}

	private MockHttpServletRequest createServletRequest(String document, String accept) throws Exception {
		SerializableGraphQlRequest request = new SerializableGraphQlRequest();
		request.setQuery(document);
		return createServletRequest(request, accept);
	}

	private MockHttpServletRequest createServletRequest(SerializableGraphQlRequest request, String accept) throws Exception {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
		servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
		servletRequest.setContent(initRequestBody(request));
		servletRequest.addHeader("Accept", accept);
		servletRequest.setAsyncSupported(true);
		return servletRequest;
	}

	private static byte[] initRequestBody(SerializableGraphQlRequest request) throws Exception {
		return new ObjectMapper().writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
	}

	private MockHttpServletResponse handleRequest(
			MockHttpServletRequest servletRequest, GraphQlHttpHandler handler) throws ServletException, IOException {

		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = handler.handleRequest(request);
		if (response instanceof AsyncServerResponse asyncResponse) {
			asyncResponse.block();
		}

		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		response.writeTo(servletRequest, servletResponse, new DefaultContext());
		return servletResponse;
	}


	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageConverter<?>> messageConverters() {
			return MESSAGE_READERS;
		}
	}

}

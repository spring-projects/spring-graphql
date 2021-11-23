/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.web.webmvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletException;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphQlHttpHandler}.
 * @author Rossen Stoyanchev
 */
public class GraphQlHttpHandlerTests {

	private static final List<HttpMessageConverter<?>> MESSAGE_READERS =
			Collections.singletonList(new MappingJackson2HttpMessageConverter());


	@Test
	void locale() throws Exception {
		GraphQlHttpHandler handler = GraphQlSetup.schemaContent("type Query { greeting: String }")
				.queryFetcher("greeting", (env) -> "Hello in " + env.getLocale())
				.toHttpHandler();
		MockHttpServletRequest servletRequest = createServletRequest("{\"query\":\"{ greeting }\"}");
		LocaleContextHolder.setLocale(Locale.FRENCH);

		try {
			MockHttpServletResponse servletResponse = handleRequest(servletRequest, handler);

			assertThat(servletResponse.getContentAsString())
					.isEqualTo("{\"data\":{\"greeting\":\"Hello in fr\"}}");
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

		MockHttpServletRequest servletRequest = createServletRequest("{\"query\":\"{ showId }\"}");

		MockHttpServletResponse servletResponse = handleRequest(servletRequest, handler);
		DocumentContext document = JsonPath.parse(servletResponse.getContentAsString());
		String id = document.read("data.showId", String.class);
		assertThat(id).hasSize(8);
	}

	private MockHttpServletRequest createServletRequest(String query) {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
		servletRequest.setContentType("application/json");
		servletRequest.setContent(query.getBytes(StandardCharsets.UTF_8));
		servletRequest.setAsyncSupported(true);
		return servletRequest;
	}

	private MockHttpServletResponse handleRequest(
			MockHttpServletRequest servletRequest, GraphQlHttpHandler handler) throws ServletException, IOException {

		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = ((AsyncServerResponse) handler.handleRequest(request)).block();

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

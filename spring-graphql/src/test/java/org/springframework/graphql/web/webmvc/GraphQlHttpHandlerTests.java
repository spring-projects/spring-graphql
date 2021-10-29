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

import graphql.GraphQL;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.Test;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.GraphQlTestUtils;
import org.springframework.graphql.TestGraphQlSource;
import org.springframework.graphql.execution.ExecutionGraphQlService;
import org.springframework.graphql.web.WebGraphQlHandler;
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
		GraphQlHttpHandler handler = createHttpHandler(
				"type Query { greeting: String }", "Query", "greeting", (env) -> "Hello in " + env.getLocale());

		MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/");
		servletRequest.setContentType("application/json");
		servletRequest.setContent("{\"query\":\"{ greeting }\"}".getBytes(StandardCharsets.UTF_8));
		servletRequest.setAsyncSupported(true);

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

	private GraphQlHttpHandler createHttpHandler(
			String schemaContent, String type, String field, DataFetcher<Object> dataFetcher) {

		GraphQL graphQl = GraphQlTestUtils.initGraphQl(schemaContent, type, field, dataFetcher);
		GraphQlService service = new ExecutionGraphQlService(new TestGraphQlSource(graphQl));
		return new GraphQlHttpHandler(WebGraphQlHandler.builder(service).build());
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

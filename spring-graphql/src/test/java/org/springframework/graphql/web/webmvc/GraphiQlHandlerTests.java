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

package org.springframework.graphql.web.webmvc;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Tests for {@link GraphiQlHandler}.
 * @author Brian Clozel
 */
class GraphiQlHandlerTests {

	private static final List<HttpMessageConverter<?>> MESSAGE_READERS = Collections.emptyList();

	private GraphiQlHandler handler = new GraphiQlHandler("/graphql", null,
			new ByteArrayResource("GRAPHIQL".getBytes(StandardCharsets.UTF_8)));

	@Test
	void shouldRedirectWithPathQueryParameter() {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/graphiql");
		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = this.handler.handleRequest(request);
		assertThat(response.statusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
		assertThat(response.headers().getLocation()).isNotNull();
		assertThat(response.headers().getLocation().toASCIIString()).isEqualTo("http://localhost/graphiql?path=/graphql");
	}

	@Test
	void shouldRedirectWithPathAndWsPathQueryParameter() {
		GraphiQlHandler wsHandler = new GraphiQlHandler("/graphql", "/graphql",
				new ByteArrayResource("GRAPHIQL".getBytes(StandardCharsets.UTF_8)));
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/graphiql");
		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = wsHandler.handleRequest(request);
		assertThat(response.statusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
		assertThat(response.headers().getLocation()).isNotNull();
		assertThat(response.headers().getLocation().toASCIIString()).isEqualTo("http://localhost/graphiql?path=/graphql&wsPath=/graphql");
	}

	@Test
	void shouldServeGraphiQlHtmlResource() throws Exception {
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/graphiql");
		servletRequest.addParameter("path", "/graphql");
		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = this.handler.handleRequest(request);
		assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.headers().getContentType()).isEqualTo(MediaType.TEXT_HTML);
		assertThat(getResponseContent(servletRequest, response)).isEqualTo("GRAPHIQL");
	}

	@Test
	void shouldConsiderContextPathWhenRedirecting() {
		GraphiQlHandler wsHandler = new GraphiQlHandler("/graphql", "/graphql",
				new ByteArrayResource("GRAPHIQL".getBytes(StandardCharsets.UTF_8)));
		MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/context/graphiql");
		servletRequest.setContextPath("/context");
		ServerRequest request = ServerRequest.create(servletRequest, MESSAGE_READERS);
		ServerResponse response = wsHandler.handleRequest(request);
		assertThat(response.statusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
		assertThat(response.headers().getLocation()).isNotNull();
		assertThat(response.headers().getLocation().toASCIIString()).isEqualTo("http://localhost/context/graphiql?path=/context/graphql&wsPath=/context/graphql");
	}

	private String getResponseContent(MockHttpServletRequest servletRequest, ServerResponse response) throws ServletException, IOException {
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		response.writeTo(servletRequest, servletResponse, new DefaultContext());
		return servletResponse.getContentAsString();
	}

	private static class DefaultContext implements ServerResponse.Context {

		@Override
		public List<HttpMessageConverter<?>> messageConverters() {
			return Collections.singletonList(new ResourceHttpMessageConverter());
		}

	}

}
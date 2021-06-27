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

package org.springframework.graphql.test.tester;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// @formatter:off

/**
 * Tests for {@link WebGraphQlTester} parameterized to:
 * <ul>
 * <li>Connect to {@link MockWebServer} and return a preset HTTP response.
 * <li>Use mock {@link WebGraphQlHandler} to return a preset {@link ExecutionResult}.
 * </ul>
 *
 * <p>
 * There is no actual handling via {@link graphql.GraphQL} in either scenario. The main
 * focus is to verify {@link GraphQlTester} request preparation and response handling.
 */
public class WebGraphQlTesterTests {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static Stream<GraphQlTesterSetup> argumentSource() {
		return Stream.of(new MockWebServerSetup(), new MockWebGraphQlHandlerSetup());
	}


	@ParameterizedTest
	@MethodSource("argumentSource")
	void headers(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response("{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.ResponseSpec spec = setup.graphQlTester().query(query)
				.header("myHeader1", "myValue1a")
				.header("myHeader1", "myValue1b")
				.headers(headers -> headers.add("myHeader2", "myValue2"))
				.execute();

		spec.path("me.name").entity(String.class).isEqualTo("Luke Skywalker");

		setup.verifyRequest((input) -> {
			assertThat(input.getQuery()).contains(query);
			assertThat(input.getHeaders().get("myHeader1")).containsExactly("myValue1a", "myValue1b");
			assertThat(input.getHeaders().getFirst("myHeader2")).isEqualTo("myValue2");
		});
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void defaultHeaders(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response("{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.ResponseSpec spec = setup.graphQlTesterBuilder()
				.defaultHeader("myHeader1", "myValue1a")
				.defaultHeader("myHeader1", "myValue1b")
				.defaultHeaders(headers -> headers.add("myHeader2", "myValue2"))
				.build()
				.query(query)
				.execute();

		spec.path("me.name").entity(String.class).isEqualTo("Luke Skywalker");

		setup.verifyRequest((input) -> {
			assertThat(input.getQuery()).contains(query);
			assertThat(input.getHeaders().get("myHeader1")).containsExactly("myValue1a", "myValue1b");
			assertThat(input.getHeaders().getFirst("myHeader2")).isEqualTo("myValue2");
		});

		setup.shutdown();
	}


	private interface GraphQlTesterSetup {

		WebGraphQlTester graphQlTester();

		WebGraphQlTester.Builder graphQlTesterBuilder();

		default void response(String data) throws Exception {
			response(data, Collections.emptyList());
		}

		default void response(GraphQLError... errors) throws Exception {
			response(null, Arrays.asList(errors));
		}

		void response(@Nullable String data, List<GraphQLError> errors) throws Exception;

		void verifyRequest(Consumer<WebInput> consumer) throws Exception;

		default void shutdown() throws Exception {
			// no-op by default
		}

	}

	private static class MockWebServerSetup implements GraphQlTesterSetup {

		private final MockWebServer server;

		private final WebGraphQlTester.Builder graphQlTesterBuilder;

		MockWebServerSetup() {
			this.server = new MockWebServer();
			this.graphQlTesterBuilder = WebGraphQlTester.builder(initWebTestClient(this.server));
		}

		private static WebTestClient initWebTestClient(MockWebServer server) {
			String baseUrl = server.url("/graphQL").toString();
			return WebTestClient.bindToServer().baseUrl(baseUrl).build();
		}

		@Override
		public WebGraphQlTester graphQlTester() {
			return this.graphQlTesterBuilder.build();
		}

		@Override
		public WebGraphQlTester.Builder graphQlTesterBuilder() {
			return this.graphQlTesterBuilder;
		}

		@Override
		public void response(@Nullable String data, List<GraphQLError> errors) throws Exception {
			StringBuilder sb = new StringBuilder("{");
			if (StringUtils.hasText(data)) {
				sb.append("\"data\":").append(data);
			}
			if (!CollectionUtils.isEmpty(errors)) {
				List<Map<String, Object>> errorSpecs = errors.stream().map(GraphQLError::toSpecification)
						.collect(Collectors.toList());

				sb.append(StringUtils.hasText(data) ? ", " : "").append("\"errors\":")
						.append(OBJECT_MAPPER.writeValueAsString(errorSpecs));
			}
			sb.append("}");

			MockResponse response = new MockResponse();
			response.setHeader("Content-Type", "application/json");
			response.setBody(sb.toString());

			this.server.enqueue(response);
		}

		@Override
		public void verifyRequest(Consumer<WebInput> consumer) throws Exception {
			assertThat(this.server.getRequestCount()).isEqualTo(1);
			RecordedRequest request = this.server.takeRequest();
			assertThat(request.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");

			HttpHeaders headers = new HttpHeaders();
			request.getHeaders().names().forEach(name -> headers.put(name, request.getHeaders().values(name)));

			String content = request.getBody().readUtf8();
			Map<String, Object> map = new ObjectMapper().readValue(content, new TypeReference<Map<String, Object>>() {});
			WebInput webInput = new WebInput(request.getRequestUrl().uri(), headers, map, null);

			consumer.accept(webInput);
		}

		@Override
		public void shutdown() throws Exception {
			this.server.shutdown();
		}

	}

	private static class MockWebGraphQlHandlerSetup implements GraphQlTesterSetup {

		private final WebGraphQlHandler handler = mock(WebGraphQlHandler.class);

		private final ArgumentCaptor<WebInput> bodyCaptor = ArgumentCaptor.forClass(WebInput.class);

		private final WebGraphQlTester.Builder graphQlTesterBuilder;

		MockWebGraphQlHandlerSetup() {
			this.graphQlTesterBuilder = WebGraphQlTester.builder(this.handler);
		}

		@Override
		public WebGraphQlTester graphQlTester() {
			return this.graphQlTesterBuilder.build();
		}

		@Override
		public WebGraphQlTester.Builder graphQlTesterBuilder() {
			return this.graphQlTesterBuilder;
		}

		@Override
		public void response(@Nullable String data, List<GraphQLError> errors) throws Exception {
			ExecutionResultImpl.Builder builder = new ExecutionResultImpl.Builder();
			if (data != null) {
				builder.data(OBJECT_MAPPER.readValue(data, new TypeReference<Map<String, Object>>() {
				}));
			}
			if (!CollectionUtils.isEmpty(errors)) {
				builder.addErrors(errors);
			}
			ExecutionResult result = builder.build();
			WebOutput output = new WebOutput(mock(WebInput.class), result);
			given(this.handler.handle(this.bodyCaptor.capture())).willReturn(Mono.just(output));
		}

		@Override
		public void verifyRequest(Consumer<WebInput> consumer) {
			WebInput webInput = this.bodyCaptor.getValue();
			consumer.accept(webInput);
		}

	}

}

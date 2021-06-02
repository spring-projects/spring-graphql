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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.language.SourceLocation;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.graphql.web.WebOutput;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link GraphQlTester} parameterized to:
 * <ul>
 * <li>Connect to {@link MockWebServer} and return a preset HTTP response.
 * <li>Use mock {@link WebGraphQlHandler} to return a preset {@link ExecutionResult}.
 * </ul>
 *
 * <p>
 * There is no actual handling via {@link graphql.GraphQL} in either scenario. The main
 * focus is to verify {@link GraphQlTester} request preparation and response handling.
 */
public class GraphQlTesterTests {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static Stream<GraphQlTesterSetup> argumentSource() {
		return Stream.of(new MockWebServerSetup(), new MockWebGraphQlHandlerSetup());
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void pathAndValueExistsAndEmptyChecks(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response("{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.ResponseSpec spec = setup.graphQlTester().query(query).execute();

		spec.path("me.name").pathExists().valueExists().valueIsNotEmpty();
		spec.path("me.friends").valueIsEmpty();
		spec.path("hero").pathDoesNotExist().valueDoesNotExist().valueIsEmpty();

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void matchesJson(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name}}";
		setup.response("{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.ResponseSpec spec = setup.graphQlTester().query(query).execute();

		spec.path("").matchesJson("{\"me\": {\"name\":\"Luke Skywalker\",\"friends\":[]}}");
		spec.path("me").matchesJson("{\"name\":\"Luke Skywalker\"}");
		spec.path("me").matchesJson("{\"friends\":[]}"); // lenient match with subset of
															// fields

		assertThatThrownBy(() -> spec.path("me").matchesJsonStrictly("{\"friends\":[]}"))
				.as("Extended fields should fail in strict mode").hasMessageContaining("Unexpected: name");

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void entity(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name}}";
		setup.response("{\"me\": {\"name\":\"Luke Skywalker\"}}");

		GraphQlTester.ResponseSpec spec = setup.graphQlTester().query(query).execute();

		MovieCharacter luke = MovieCharacter.create("Luke Skywalker");
		MovieCharacter han = MovieCharacter.create("Han Solo");
		AtomicReference<MovieCharacter> personRef = new AtomicReference<>();

		MovieCharacter actual = spec.path("me").entity(MovieCharacter.class).isEqualTo(luke).isNotEqualTo(han)
				.satisfies(personRef::set).matches((movieCharacter) -> personRef.get().equals(movieCharacter))
				.isSameAs(personRef.get()).isNotSameAs(luke).get();

		assertThat(actual.getName()).isEqualTo("Luke Skywalker");

		spec.path("").entity(new ParameterizedTypeReference<Map<String, MovieCharacter>>() {
		}).isEqualTo(Collections.singletonMap("me", luke));

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void entityList(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response("{" + "  \"me\":{" + "      \"name\":\"Luke Skywalker\","
				+ "      \"friends\":[{\"name\":\"Han Solo\"}, {\"name\":\"Leia Organa\"}]" + "  }" + "}");

		GraphQlTester.ResponseSpec spec = setup.graphQlTester().query(query).execute();

		MovieCharacter han = MovieCharacter.create("Han Solo");
		MovieCharacter leia = MovieCharacter.create("Leia Organa");
		MovieCharacter jabba = MovieCharacter.create("Jabba the Hutt");

		List<MovieCharacter> actual = spec.path("me.friends").entityList(MovieCharacter.class).contains(han)
				.containsExactly(han, leia).doesNotContain(jabba).hasSize(2).hasSizeGreaterThan(1).hasSizeLessThan(3)
				.get();

		assertThat(actual).containsExactly(han, leia);

		spec.path("me.friends").entityList(new ParameterizedTypeReference<MovieCharacter>() {
		}).containsExactly(han, leia);

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void operationNameAndVariables(GraphQlTesterSetup setup) throws Exception {

		String query = "query HeroNameAndFriends($episode: Episode) {" + "  hero(episode: $episode) {" + "    name"
				+ "  }" + "}";

		setup.response("{\"hero\": {\"name\":\"R2-D2\"}}");

		GraphQlTester.ResponseSpec spec = setup.graphQlTester().query(query).operationName("HeroNameAndFriends")
				.variable("episode", "JEDI").variables((map) -> map.put("foo", "bar")).execute();

		spec.path("hero").entity(MovieCharacter.class).isEqualTo(MovieCharacter.create("R2-D2"));

		setup.verifyRequest((input) -> {
			assertThat(input.getQuery()).contains(query);
			assertThat(input.getOperationName()).isEqualTo("HeroNameAndFriends");
			assertThat(input.getVariables()).hasSize(2);
			assertThat(input.getVariables()).containsEntry("episode", "JEDI");
			assertThat(input.getVariables()).containsEntry("foo", "bar");
		});
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void errorsCheckedOnExecuteAndVerify(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response(GraphqlErrorBuilder.newError().message("Invalid query").build());

		assertThatThrownBy(() -> setup.graphQlTester().query(query).executeAndVerify())
				.hasMessageContaining("Response has 1 unexpected error(s).");

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void errorsCheckedOnTraverse(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response(GraphqlErrorBuilder.newError().message("Invalid query").build());

		assertThatThrownBy(() -> setup.graphQlTester().query(query).execute().path("me"))
				.hasMessageContaining("Response has 1 unexpected error(s).");

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void errorsPartiallyFiltered(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response(GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		assertThatThrownBy(() -> setup.graphQlTester().query(query).execute().errors()
				.filter((error) -> error.getMessage().equals("some error")).verify())
						.hasMessageContaining("Response has 1 unexpected error(s) of 2 total.");

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void errorsFiltered(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response(GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		setup.graphQlTester().query(query).execute().errors().filter((error) -> error.getMessage().startsWith("some "))
				.verify().path("me").pathDoesNotExist();

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	@ParameterizedTest
	@MethodSource("argumentSource")
	void errorsConsumed(GraphQlTesterSetup setup) throws Exception {

		String query = "{me {name, friends}}";
		setup.response(
				GraphqlErrorBuilder.newError().message("Invalid query").location(new SourceLocation(1, 2)).build());

		setup.graphQlTester().query(query).execute().errors().satisfy((errors) -> {
			assertThat(errors).hasSize(1);
			assertThat(errors.get(0).getMessage()).isEqualTo("Invalid query");
			assertThat(errors.get(0).getLocations()).hasSize(1);
			assertThat(errors.get(0).getLocations().get(0).getLine()).isEqualTo(1);
			assertThat(errors.get(0).getLocations().get(0).getColumn()).isEqualTo(2);
		}).path("me").pathDoesNotExist();

		setup.verifyRequest((input) -> assertThat(input.getQuery()).contains(query));
		setup.shutdown();
	}

	private interface GraphQlTesterSetup {

		GraphQlTester graphQlTester();

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

		private final GraphQlTester graphQlTester;

		MockWebServerSetup() {
			this.server = new MockWebServer();
			this.graphQlTester = GraphQlTester.create(initWebTestClient(this.server));
		}

		private static WebTestClient initWebTestClient(MockWebServer server) {
			String baseUrl = server.url("/graphQL").toString();
			return WebTestClient.bindToServer().baseUrl(baseUrl).build();
		}

		@Override
		public GraphQlTester graphQlTester() {
			return this.graphQlTester;
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

			String content = request.getBody().readUtf8();
			Map<String, Object> map = new ObjectMapper().readValue(content, new TypeReference<Map<String, Object>>() {
			});
			WebInput webInput = new WebInput(request.getRequestUrl().uri(), new HttpHeaders(), map, null);

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

		private final GraphQlTester graphQlTester;

		MockWebGraphQlHandlerSetup() {
			this.graphQlTester = GraphQlTester.create(this.handler);
		}

		@Override
		public GraphQlTester graphQlTester() {
			return this.graphQlTester;
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

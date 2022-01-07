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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlService;
import org.springframework.graphql.RequestInput;
import org.springframework.graphql.RequestOutput;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link GraphQlTester}.
 *
 * <p>
 * There is no actual handling via {@link graphql.GraphQL} in either scenario. The main
 * focus is to verify {@link GraphQlTester} request preparation and response handling.
 */
public class GraphQlTesterTests {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


	private final GraphQlService service = mock(GraphQlService.class);

	private final GraphQlTester graphQlTester = GraphQlTester.create(this.service);

	private final ArgumentCaptor<RequestInput> inputCaptor = ArgumentCaptor.forClass(RequestInput.class);


	@Test
	void pathAndValueExistsAndEmptyChecks() throws Exception {

		String query = "{me {name, friends}}";
		setResponse("{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.ResponseSpec spec = this.graphQlTester.query(query).execute();

		spec.path("me.name").pathExists().valueExists().valueIsNotEmpty();
		spec.path("me.friends").valueIsEmpty();
		spec.path("hero").pathDoesNotExist().valueDoesNotExist().valueIsEmpty();

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void matchesJson() throws Exception {

		String query = "{me {name}}";
		setResponse("{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.ResponseSpec spec = this.graphQlTester.query(query).execute();

		spec.path("").matchesJson("{\"me\": {\"name\":\"Luke Skywalker\",\"friends\":[]}}");
		spec.path("me").matchesJson("{\"name\":\"Luke Skywalker\"}");
		spec.path("me").matchesJson("{\"friends\":[]}"); // lenient match with subset of
															// fields

		assertThatThrownBy(() -> spec.path("me").matchesJsonStrictly("{\"friends\":[]}"))
				.as("Extended fields should fail in strict mode")
				.hasMessageContaining("Unexpected: name");

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void entity() throws Exception {

		String query = "{me {name}}";
		setResponse("{\"me\": {\"name\":\"Luke Skywalker\"}}");

		GraphQlTester.ResponseSpec spec = this.graphQlTester.query(query).execute();

		MovieCharacter luke = MovieCharacter.create("Luke Skywalker");
		MovieCharacter han = MovieCharacter.create("Han Solo");
		AtomicReference<MovieCharacter> personRef = new AtomicReference<>();

		MovieCharacter actual = spec.path("me")
				.entity(MovieCharacter.class)
				.isEqualTo(luke)
				.isNotEqualTo(han)
				.satisfies(personRef::set)
				.matches((movieCharacter) -> personRef.get().equals(movieCharacter))
				.isSameAs(personRef.get())
				.isNotSameAs(luke).get();

		assertThat(actual.getName()).isEqualTo("Luke Skywalker");

		spec.path("")
				.entity(new ParameterizedTypeReference<Map<String, MovieCharacter>>() {})
				.isEqualTo(Collections.singletonMap("me", luke));

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void entityList() throws Exception {

		String query = "{me {name, friends}}";
		setResponse("{" +
				"  \"me\":{" +
				"      \"name\":\"Luke Skywalker\","
				+ "      \"friends\":[{\"name\":\"Han Solo\"}, {\"name\":\"Leia Organa\"}]" +
				"  }" +
				"}");

		GraphQlTester.ResponseSpec spec = this.graphQlTester.query(query).execute();

		MovieCharacter han = MovieCharacter.create("Han Solo");
		MovieCharacter leia = MovieCharacter.create("Leia Organa");
		MovieCharacter jabba = MovieCharacter.create("Jabba the Hutt");

		List<MovieCharacter> actual = spec.path("me.friends")
				.entityList(MovieCharacter.class)
				.contains(han)
				.containsExactly(han, leia)
				.doesNotContain(jabba)
				.hasSize(2)
				.hasSizeGreaterThan(1)
				.hasSizeLessThan(3)
				.get();

		assertThat(actual).containsExactly(han, leia);

		spec.path("me.friends")
				.entityList(new ParameterizedTypeReference<MovieCharacter>() {})
				.containsExactly(han, leia);

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void operationNameAndVariables() throws Exception {

		String query = "query HeroNameAndFriends($episode: Episode) {" +
				"  hero(episode: $episode) {" +
				"    name"
				+ "  }" +
				"}";

		setResponse("{\"hero\": {\"name\":\"R2-D2\"}}");

		GraphQlTester.ResponseSpec spec = this.graphQlTester.query(query)
				.operationName("HeroNameAndFriends")
				.variable("episode", "JEDI")
				.variable("foo", "bar")
				.variable("keyOnly", null)
				.execute();

		spec.path("hero").entity(MovieCharacter.class).isEqualTo(MovieCharacter.create("R2-D2"));

		RequestInput input = this.inputCaptor.getValue();
		assertThat(input.getQuery()).contains(query);
		assertThat(input.getOperationName()).isEqualTo("HeroNameAndFriends");
		assertThat(input.getVariables()).hasSize(3);
		assertThat(input.getVariables()).containsEntry("episode", "JEDI");
		assertThat(input.getVariables()).containsEntry("foo", "bar");
		assertThat(input.getVariables()).containsEntry("keyOnly", null);
	}

	@Test
	void errorsCheckedOnExecuteAndVerify() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(GraphqlErrorBuilder.newError().message("Invalid query").build());

		assertThatThrownBy(() -> this.graphQlTester.query(query).executeAndVerify())
				.hasMessageContaining("Response has 1 unexpected error(s).");

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void errorsCheckedOnTraverse() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(GraphqlErrorBuilder.newError().message("Invalid query").build());

		assertThatThrownBy(() -> this.graphQlTester.query(query).execute().path("me"))
				.hasMessageContaining("Response has 1 unexpected error(s).");

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void errorsPartiallyFiltered() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		assertThatThrownBy(() ->
				this.graphQlTester.query(query)
						.execute()
						.errors()
						.filter((error) -> error.getMessage().equals("some error"))
						.verify())
				.hasMessageContaining("Response has 1 unexpected error(s) of 2 total.");

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void errorsFiltered() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		this.graphQlTester.query(query)
				.execute()
				.errors()
				.filter((error) -> error.getMessage().startsWith("some "))
				.verify()
				.path("me")
				.pathDoesNotExist();

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void errorsFilteredGlobally() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		GraphQlTester.builder(this.service)
				.errorFilter((error) -> error.getMessage().startsWith("some "))
				.build()
				.query(query)
				.execute()
				.errors()
				.verify()
				.path("me")
				.pathDoesNotExist();

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void errorsExpected() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		this.graphQlTester.query(query)
				.execute()
				.errors()
				.expect((error) -> error.getMessage().startsWith("some "))
				.verify()
				.path("me")
				.pathDoesNotExist();

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	@Test
	void errorsExpectedButNotFound() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		assertThatThrownBy(() ->
				this.graphQlTester.query(query)
						.execute()
						.errors().expect((error) -> error.getMessage().startsWith("another ")))
				.hasMessageStartingWith("No matching errors.");
	}

	@Test
	void errorsConsumed() throws Exception {

		String query = "{me {name, friends}}";
		setResponse(GraphqlErrorBuilder.newError()
				.message("Invalid query")
				.location(new SourceLocation(1, 2))
				.build());

		this.graphQlTester.query(query)
				.execute()
				.errors()
				.satisfy((errors) -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getMessage()).isEqualTo("Invalid query");
					assertThat(errors.get(0).getLocations()).hasSize(1);
					assertThat(errors.get(0).getLocations().get(0).getLine()).isEqualTo(1);
					assertThat(errors.get(0).getLocations().get(0).getColumn()).isEqualTo(2);
				})
				.path("me")
				.pathDoesNotExist();

		assertThat(this.inputCaptor.getValue().getQuery()).contains(query);
	}

	private void setResponse(String data) throws Exception {
		setResponse(data, Collections.emptyList());
	}

	private void setResponse(GraphQLError... errors) throws Exception {
		setResponse(null, Arrays.asList(errors));
	}

	private void setResponse(@Nullable String data, List<GraphQLError> errors) throws Exception {
		ExecutionResultImpl.Builder builder = new ExecutionResultImpl.Builder();
		if (data != null) {
			builder.data(OBJECT_MAPPER.readValue(data, new TypeReference<Map<String, Object>>() {}));
		}
		if (!CollectionUtils.isEmpty(errors)) {
			builder.addErrors(errors);
		}
		RequestInput input = new RequestInput("{}", null, null, null, "1");
		ExecutionResult result = builder.build();
		given(this.service.execute(this.inputCaptor.capture())).willReturn(Mono.just(new RequestOutput(input, result)));
	}

}

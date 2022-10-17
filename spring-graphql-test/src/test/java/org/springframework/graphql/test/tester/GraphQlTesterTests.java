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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import graphql.GraphqlErrorBuilder;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.Test;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link GraphQlTester} with a mock {@link ExecutionGraphQlService}.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlTesterTests extends GraphQlTesterTestSupport {

	@Test
	void hasValue() {

		String document = "{me {name, friends}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.Response response = graphQlTester().document(document).execute();
		response.path("me.name").hasValue();
		response.path("me.friends").hasValue();
		response.path("me[?(@.name == 'Luke Skywalker')].name").hasValue(); // gh-377

		assertThatThrownBy(() -> response.path("hero").hasValue())
				.hasMessageContaining("No value at JSON path \"$.data.hero\"");

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void valueIsNull() {

		String document = "{me {name, friends}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":null, \"friends\":null}}");

		GraphQlTester.Response response = graphQlTester().document(document).execute();

		response.path("me.name").valueIsNull();
		response.path("me.friends").valueIsNull();

		assertThatThrownBy(() -> response.path("me").valueIsNull())
				.hasMessageContaining("Expected null value at JSON path");

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void valueIsEmptyList() {

		String document = "{me {name, friends}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.Response response = graphQlTester().document(document).execute();
		response.path("me.friends").hasValue().entityList(MovieCharacter.class).hasSize(0);
	}

	@Test
	void pathDoesNotExist() {
		String document = "{me {name, friends}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.Response response = graphQlTester().document(document).execute();

		response.path("hero").pathDoesNotExist();

		assertThatThrownBy(() -> response.path("me.name").pathDoesNotExist())
				.hasMessageContaining("Expected no value at JSON path");
	}

	@Test
	void matchesJson() {

		String document = "{me {name}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		GraphQlTester.Response response = graphQlTester().document(document).execute();

		response.path("").matchesJson("{\"me\": {\"name\":\"Luke Skywalker\",\"friends\":[]}}");
		response.path("me").matchesJson("{\"name\":\"Luke Skywalker\"}");
		response.path("me").matchesJson("{\"friends\":[]}"); // lenient match with subset of fields

		assertThatThrownBy(() -> response.path("me").matchesJsonStrictly("{\"friends\":[]}"))
				.as("Extended fields should fail in strict mode")
				.hasMessageContaining("Unexpected: name");

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void entity() {

		String document = "{me {name}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\"}}");

		GraphQlTester.Response response = graphQlTester().document(document).execute();

		MovieCharacter luke = MovieCharacter.create("Luke Skywalker");
		MovieCharacter han = MovieCharacter.create("Han Solo");
		AtomicReference<MovieCharacter> personRef = new AtomicReference<>();

		MovieCharacter actual = response.path("me").entity(MovieCharacter.class)
				.isEqualTo(luke)
				.isNotEqualTo(han)
				.satisfies(personRef::set)
				.matches((movieCharacter) -> personRef.get().equals(movieCharacter))
				.isSameAs(personRef.get())
				.isNotSameAs(luke)
				.get();

		assertThat(actual.getName()).isEqualTo("Luke Skywalker");

		response.path("")
				.entity(new ParameterizedTypeReference<Map<String, MovieCharacter>>() {})
				.isEqualTo(Collections.singletonMap("me", luke));

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void entityList() {

		String document = "{me {name, friends}}";
		getGraphQlService().setDataAsJson(document,
				"{" +
				"  \"me\":{" +
				"      \"name\":\"Luke Skywalker\","
				+ "    \"friends\":[{\"name\":\"Han Solo\"}, {\"name\":\"Leia Organa\"}]" +
				"  }" +
				"}");

		GraphQlTester.Response response = graphQlTester().document(document).execute();

		MovieCharacter han = MovieCharacter.create("Han Solo");
		MovieCharacter leia = MovieCharacter.create("Leia Organa");
		MovieCharacter jabba = MovieCharacter.create("Jabba the Hutt");

		GraphQlTester.EntityList<MovieCharacter> entityList =
				response.path("me.friends").entityList(MovieCharacter.class);

		List<MovieCharacter> actual = entityList
				.contains(han)
				.containsExactly(han, leia)
				.doesNotContain(jabba)
				.hasSize(2)
				.hasSizeGreaterThan(1)
				.hasSizeLessThan(3)
				.get();

		assertThat(actual).containsExactly(han, leia);

		assertThatThrownBy(() -> entityList.containsExactly(leia, han))
				.as("Should be exactly the same order")
				.hasMessage("Expecting list " +
						"[MovieCharacter[name='Han Solo'], MovieCharacter[name='Leia Organa']] " +
						"at path 'me.friends' to contain exactly " +
						"[MovieCharacter[name='Leia Organa'], MovieCharacter[name='Han Solo']]\n" +
						"Request: document='{me {name, friends}}'");

		response.path("me.friends")
				.entityList(new ParameterizedTypeReference<MovieCharacter>() {})
				.containsExactly(han, leia);

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void operationNameAndVariables() {

		String document = "query HeroNameAndFriends($episode: Episode) {" +
				"  hero(episode: $episode) {" +
				"    name"
				+ "  }" +
				"}";

		getGraphQlService().setDataAsJson(document, "{\"hero\": {\"name\":\"R2-D2\"}}");

		GraphQlTester.Response response = graphQlTester().document(document)
				.operationName("HeroNameAndFriends")
				.variable("episode", "JEDI")
				.variable("foo", "bar")
				.variable("keyOnly", null)
				.execute();

		response.path("hero").entity(MovieCharacter.class).isEqualTo(MovieCharacter.create("R2-D2"));

		ExecutionGraphQlRequest request = getGraphQlService().getGraphQlRequest();
		assertThat(request.getDocument()).contains(document);
		assertThat(request.getOperationName()).isEqualTo("HeroNameAndFriends");
		assertThat(request.getVariables()).hasSize(3);
		assertThat(request.getVariables()).containsEntry("episode", "JEDI");
		assertThat(request.getVariables()).containsEntry("foo", "bar");
		assertThat(request.getVariables()).containsEntry("keyOnly", null);
	}

	@Test
	void protocolExtensions() {
		String document = "{me {name, friends}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		graphQlTester().document(document)
				.extension("firstExt", Collections.singletonMap("key", "value"))
				.extension("secondExt", "value")
				.execute();

		ExecutionGraphQlRequest request = getGraphQlService().getGraphQlRequest();
		assertThat(request.getDocument()).contains(document);
		assertThat(request.getExtensions()).hasSize(2);
		assertThat(request.getExtensions()).containsEntry("firstExt", Collections.singletonMap("key", "value"))
				.containsEntry("secondExt", "value");
	}

	@Test
	void errorsEmptyOnExecuteAndVerify() {

		String document = "{me {name, friends}}";
		getGraphQlService().setDataAsJson(document, "{\"me\": {\"name\":\"Luke Skywalker\", \"friends\":[]}}");

		graphQlTester().document(document).executeAndVerify();

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void errorsCheckedOnExecuteAndVerify() {

		String document = "{me {name, friends}}";
		getGraphQlService().setError(document, builder -> builder.message("Invalid query"));

		assertThatThrownBy(() -> graphQlTester().document(document).executeAndVerify())
				.hasMessageContaining("Response has 1 unexpected error(s)");

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void errorsCheckedOnTraverse() {

		String document = "{me {name, friends}}";
		getGraphQlService().setError(document, builder -> builder.message("Invalid query"));

		assertThatThrownBy(() -> graphQlTester().document(document).execute().path("me"))
				.hasMessageContaining("Response has 1 unexpected error(s)");

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void errorsPartiallyFiltered() {

		String document = "{me {name, friends}}";
		getGraphQlService().setErrors(
				document,
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		assertThatThrownBy(() ->
				graphQlTester().document(document)
						.execute()
						.errors()
						.filter((error) -> error.getMessage().equals("some error"))
						.verify())
				.hasMessageContaining("Response has 1 unexpected error(s) of 2 total.");

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void errorsFiltered() {

		String document = "{me {name, friends}}";
		getGraphQlService().setErrors(
				document,
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		graphQlTester().document(document)
				.execute()
				.errors()
				.filter((error) -> error.getMessage().startsWith("some "))
				.verify()
				.path("me")
				.pathDoesNotExist();

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void errorsExpected() {

		String document = "{me {name, friends}}";
		getGraphQlService().setErrors(
				document,
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		graphQlTester().document(document)
				.execute()
				.errors()
				.expect((error) -> error.getMessage().startsWith("some "))
				.verify()
				.path("me").pathDoesNotExist();

		assertThat(getActualRequestDocument()).contains(document);
	}

	@Test
	void errorsExpectedButNotFound() {

		String document = "{me {name, friends}}";
		getGraphQlService().setErrors(
				document,
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		assertThatThrownBy(() ->
				graphQlTester().document(document)
						.execute()
						.errors().expect((error) -> error.getMessage().startsWith("another ")))
				.hasMessageStartingWith("No matching errors.");
	}

	@Test
	void errorsConsumed() {

		String document = "{me {name, friends}}";
		getGraphQlService().setError(document, builder ->
				builder.message("Invalid query").location(new SourceLocation(1, 2)).build());

		graphQlTester().document(document)
				.execute()
				.errors()
				.satisfy((errors) -> {
					assertThat(errors).hasSize(1);
					assertThat(errors.get(0).getMessage()).isEqualTo("Invalid query");
					assertThat(errors.get(0).getLocations()).hasSize(1);
					assertThat(errors.get(0).getLocations().get(0).getLine()).isEqualTo(1);
					assertThat(errors.get(0).getLocations().get(0).getColumn()).isEqualTo(2);
				})
				.path("me").pathDoesNotExist();

		assertThat(getActualRequestDocument()).contains(document);
	}

}

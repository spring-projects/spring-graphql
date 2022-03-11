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
package org.springframework.graphql.client;

import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
import org.junit.jupiter.api.Test;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Test {@link GraphQlClient} with a mock/test transport.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlClientTests extends GraphQlClientTestSupport {

	@Test
	void entity() {
		String document = "{me {name}}";
		setMockResponse("{\"me\": {\"name\":\"Luke Skywalker\"}}");

		MovieCharacter character = MovieCharacter.create("Luke Skywalker");

		ClientGraphQlResponse response = execute(document);
		assertThat(response.isValid()).isTrue();

		ResponseField field = response.field("me");
		assertThat(field.isValid()).isTrue();
		assertThat(field.toEntity(MovieCharacter.class)).isEqualTo(character);

		Map<String, MovieCharacter> map = response.toEntity(new ParameterizedTypeReference<Map<String, MovieCharacter>>() {});
		assertThat(map).containsEntry("me", character);
		assertThat(request().getDocument()).contains(document);
	}

	@Test
	void entityList() {

		String document = "{me {name, friends}}";
		setMockResponse("{" +
				"  \"me\":{" +
				"      \"name\":\"Luke Skywalker\","
				+ "    \"friends\":[{\"name\":\"Han Solo\"}, {\"name\":\"Leia Organa\"}]" +
				"  }" +
				"}");

		MovieCharacter han = MovieCharacter.create("Han Solo");
		MovieCharacter leia = MovieCharacter.create("Leia Organa");

		ClientGraphQlResponse response = execute(document);
		assertThat(response.isValid()).isTrue();

		ResponseField field = response.field("me.friends");
		assertThat(field.toEntityList(MovieCharacter.class)).containsExactly(han, leia);
		assertThat(field.toEntityList(new ParameterizedTypeReference<MovieCharacter>() {})).containsExactly(han, leia);

		assertThat(request().getDocument()).contains(document);
	}

	@Test
	void operationNameAndVariables() {

		String document = "query HeroNameAndFriends($episode: Episode) {" +
				"  hero(episode: $episode) {" +
				"    name"
				+ "  }" +
				"}";
		setMockResponse("{\"hero\": {\"name\":\"R2-D2\"}}");

		ClientGraphQlResponse response = graphQlClient().document(document)
				.operationName("HeroNameAndFriends")
				.variable("episode", "JEDI")
				.variable("foo", "bar")
				.variable("keyOnly", null)
				.execute()
				.block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.isValid()).isTrue();
		assertThat(response.field("hero").toEntity(MovieCharacter.class)).isEqualTo(MovieCharacter.create("R2-D2"));

		GraphQlRequest request = request();
		assertThat(request.getDocument()).contains(document);
		assertThat(request.getOperationName()).isEqualTo("HeroNameAndFriends");
		assertThat(request.getVariables()).hasSize(3);
		assertThat(request.getVariables()).containsEntry("episode", "JEDI");
		assertThat(request.getVariables()).containsEntry("foo", "bar");
		assertThat(request.getVariables()).containsEntry("keyOnly", null);
	}

	@Test
	void requestFailureBeforeExecution() {

		String document = "{invalid";
		setMockResponse(new ValidationError(ValidationErrorType.InvalidSyntax));

		ClientGraphQlResponse response = execute(document);

		assertThat(response.isValid()).isFalse();
		assertThat(response.field("me").isValid()).isFalse();
	}

	@Test
	void errors() {

		String document = "{me {name, friends}}";
		setMockResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		ClientGraphQlResponse response = execute(document);
		assertThat(response.isValid()).isFalse();

		assertThat(response.getErrors())
				.extracting(GraphQLError::getMessage)
				.containsExactly("some error", "some other error");
	}

	private ClientGraphQlResponse execute(String document) {
		ClientGraphQlResponse response = graphQlClient().document(document).execute().block(TIMEOUT);
		assertThat(response).isNotNull();
		return response;
	}

}

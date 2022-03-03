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

import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
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

		GraphQlClient.ResponseSpec spec = execute(document);

		MovieCharacter luke = MovieCharacter.create("Luke Skywalker");
		assertThat(spec.toEntity("me", MovieCharacter.class)).isEqualTo(luke);

		Map<String, MovieCharacter> map = spec.toEntity("", new ParameterizedTypeReference<Map<String, MovieCharacter>>() {});
		assertThat(map).containsEntry("me", luke);

		assertThat(request().getDocument()).contains(document);
	}

	@Test
	void entityList() {

		String document = "{me {name, friends}}";
		setMockResponse("{" +
				"  \"me\":{" +
				"      \"name\":\"Luke Skywalker\","
				+ "      \"friends\":[{\"name\":\"Han Solo\"}, {\"name\":\"Leia Organa\"}]" +
				"  }" +
				"}");

		GraphQlClient.ResponseSpec spec = execute(document);

		MovieCharacter han = MovieCharacter.create("Han Solo");
		MovieCharacter leia = MovieCharacter.create("Leia Organa");

		List<MovieCharacter> characters = spec.toEntityList("me.friends", MovieCharacter.class);
		assertThat(characters).containsExactly(han, leia);

		characters = spec.toEntityList("me.friends", new ParameterizedTypeReference<MovieCharacter>() {});
		assertThat(characters).containsExactly(han, leia);

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

		GraphQlClient.ResponseSpec spec = graphQlClient().document(document)
				.operationName("HeroNameAndFriends")
				.variable("episode", "JEDI")
				.variable("foo", "bar")
				.variable("keyOnly", null)
				.execute()
				.block(TIMEOUT);

		assertThat(spec).isNotNull();

		MovieCharacter character = spec.toEntity("hero", MovieCharacter.class);
		assertThat(character).isEqualTo(MovieCharacter.create("R2-D2"));

		GraphQlRequest request = request();
		assertThat(request.getDocument()).contains(document);
		assertThat(request.getOperationName()).isEqualTo("HeroNameAndFriends");
		assertThat(request.getVariables()).hasSize(3);
		assertThat(request.getVariables()).containsEntry("episode", "JEDI");
		assertThat(request.getVariables()).containsEntry("foo", "bar");
		assertThat(request.getVariables()).containsEntry("keyOnly", null);
	}

	@Test
	void errors() {

		String document = "{me {name, friends}}";
		setMockResponse(
				GraphqlErrorBuilder.newError().message("some error").build(),
				GraphqlErrorBuilder.newError().message("some other error").build());

		GraphQlClient.ResponseSpec spec = execute(document);

		assertThat(spec.errors()).extracting(GraphQLError::getMessage)
				.containsExactly("some error", "some other error");
	}

	private GraphQlClient.ResponseSpec execute(String document) {
		GraphQlClient.ResponseSpec spec = graphQlClient().document(document).execute().block(TIMEOUT);
		assertThat(spec).isNotNull();
		return spec;
	}

}

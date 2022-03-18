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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.ResultPath;
import graphql.schema.GraphQLObjectType;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
import org.junit.jupiter.api.Test;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * Test {@link GraphQlClient} with a mock/test transport.
 *
 * @author Rossen Stoyanchev
 */
public class GraphQlClientTests extends GraphQlClientTestSupport {

	@Test
	void retrieveEntity() {

		String document = "mockRequest1";
		initDataResponse(document, "{\"me\": {\"name\":\"Luke Skywalker\"}}");

		MovieCharacter movieCharacter = graphQlClient().document(document)
				.retrieve("me").toEntity(MovieCharacter.class)
				.block(TIMEOUT);

		assertThat(movieCharacter).isEqualTo(MovieCharacter.create("Luke Skywalker"));
	}

	@Test
	void retrieveEntityList() {

		String document = "mockRequest1";
		initDataResponse(document, "{" +
				"  \"me\":{" +
				"      \"name\":\"Luke Skywalker\","
				+ "    \"friends\":[{\"name\":\"Han Solo\"}, {\"name\":\"Leia Organa\"}]" +
				"  }" +
				"}");

		List<MovieCharacter> movieCharacters = graphQlClient().document(document)
				.retrieve("me.friends")
				.toEntityList(MovieCharacter.class)
				.block(TIMEOUT);

		assertThat(movieCharacters).containsExactly(
				MovieCharacter.create("Han Solo"), MovieCharacter.create("Leia Organa"));
	}

	@Test
	void retrieveAndDecodeDataMap() {

		String document = "mockRequest1";
		initDataResponse(document, "{\"me\": {\"name\":\"Luke Skywalker\"}}");

		Map<String, MovieCharacter> map = graphQlClient().document(document)
				.retrieve("").toEntity(new ParameterizedTypeReference<Map<String, MovieCharacter>>() {})
				.block(TIMEOUT);

		assertThat(map).containsEntry("me", MovieCharacter.create("Luke Skywalker"));
	}

	@Test
	void retrieveWithOperationNameAndVariables() {

		String document = "mockRequest1";
		String operationName = "HeroNameAndFriends";

		Map<String, Object> vars = new HashMap<>();
		vars.put("episode", "JEDI");
		vars.put("foo", "bar");
		vars.put("keyOnly", null);

		GraphQlRequest request = new GraphQlRequest("mockRequest1", "HeroNameAndFriends", vars);
		initResponse(request, "{\"hero\": {\"name\":\"R2-D2\"}}");

		MovieCharacter character = graphQlClient().document(document)
				.operationName(operationName)
				.variables(vars)
				.variable("keyOnly", null)
				.retrieve("hero")
				.toEntity(MovieCharacter.class)
				.block(TIMEOUT);

		assertThat(character).isEqualTo(MovieCharacter.create("R2-D2"));
	}

	@Test
	void retrieveInvalidResponse() {

		String document = "errorsOnlyResponse";
		initErrorResponse(document, new ValidationError(ValidationErrorType.InvalidSyntax));
		testRetrieveFieldAccessException(document, "me");

		document = "nullDataResponse";
		GraphQLObjectType type = GraphQLObjectType.newObject().name("n").build();
		initResponse(document, "null", new NonNullableValueCoercedAsNullException("f", new ArrayList<>(), type));
		testRetrieveFieldAccessException(document, "me");
	}

	@Test
	void retrievePartialResponse() {

		String document = "fieldErrorResponse";
		initResponse(document, "{\"me\": {\"name\":null}}", errorForPath("/me/name"));

		MovieCharacter character = graphQlClient().document(document).retrieve("me").toEntity(MovieCharacter.class).block();
		assertThat(character).isNotNull().extracting(MovieCharacter::getName).isNull();

		testRetrieveFieldAccessException(document, "me.name");
	}

	private void testRetrieveFieldAccessException(String document, String path) {
		assertThatThrownBy(() ->
				graphQlClient().document(document)
						.retrieve(path).toEntity(MovieCharacter.class)
						.block(TIMEOUT))
				.isInstanceOf(FieldAccessException.class);
	}

	@Test
	void executeInvalidResponse() {

		String document = "errorsOnlyResponse";
		initErrorResponse(document, new ValidationError(ValidationErrorType.InvalidSyntax));
		testExecuteFailedResponse(document);

		document = "nullDataResponse";
		GraphQLObjectType type = GraphQLObjectType.newObject().name("n").build();
		initResponse(document, "null", new NonNullableValueCoercedAsNullException("f", new ArrayList<>(), type));
		testExecuteFailedResponse(document);
	}

	private void testExecuteFailedResponse(String document) {

		ClientGraphQlResponse response =
				graphQlClient().document(document).execute().block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.isValid()).isFalse();
		assertThat(response.field("me").hasValue()).isFalse();

		assertThatThrownBy(() -> response.field("me").toEntity(MovieCharacter.class))
				.isInstanceOf(FieldAccessException.class);
	}

	@Test
	void executePartialResponse() {

		String document = "fieldErrorResponse";
		initResponse(document, "{\"me\": {\"name\":null}}", errorForPath("/me/name"));
		testRetrieveFieldAccessException(document, "me.name");

		ClientGraphQlResponse response =
				graphQlClient().document(document).execute().block(TIMEOUT);

		assertThat(response).isNotNull();
		assertThat(response.isValid())
				.as("Partial response with field errors should be considered valid")
				.isTrue();

		GraphQlResponseField field = response.field("me");
		assertThat(field.hasValue()).isTrue();
		assertThat(field.getErrors()).hasSize(1);
		assertThat(field.getErrors().get(0).getParsedPath()).containsExactly("me", "name");
		assertThat(field.toEntity(MovieCharacter.class))
				.as("Decoding with nested field error should not be precluded")
				.isNotNull();

		GraphQlResponseField nameField = response.field("me.name");
		assertThat(nameField.hasValue()).isFalse();
		assertThat(nameField.getError()).isNotNull();
		assertThat(nameField.getError().getParsedPath()).containsExactly("me", "name");
		assertThatThrownBy(() -> nameField.toEntity(String.class))
				.as("Decoding field null with direct field error should be rejected")
				.isInstanceOf(FieldAccessException.class);

		GraphQlResponseField nonExistingField = response.field("me.name.other");
		assertThat(nonExistingField.hasValue()).isFalse();
		assertThat(nameField.getError()).isNotNull();
		assertThat(nameField.getError().getParsedPath()).containsExactly("me", "name");
	}

	private GraphQLError errorForPath(String errorPath) {
		return GraphqlErrorBuilder.newError()
				.message("Test error")
				.path(ResultPath.parse(errorPath).toList()).build();
	}

}

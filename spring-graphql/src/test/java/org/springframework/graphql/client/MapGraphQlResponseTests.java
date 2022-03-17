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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.ResultPath;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;


/**
 * Unit tests for {@link MapGraphQlResponse}.
 * @author Rossen Stoyanchev
 */
public class MapGraphQlResponseTests {

	private static final ObjectMapper mapper = new ObjectMapper();


	@Test
	void parsePath() {
		testParsePath("");
		testParsePath("        \t  ");
		testParsePath("me.name", "me", "name");
		testParsePath("me.friends[1]", "me", "friends", 1);
		testParsePath("me.friends[1].name", "me", "friends", 1, "name");
		testParsePath(" me . name ", " me ", " name ");
	}

	private static void testParsePath(String path, Object... expected) {
		assertThat(MapGraphQlResponse.parseFieldPath(path)).containsExactly(expected);
	}

	@Test
	void parsePathInvalid() {
		testParseInvalidPath(".me");
		testParseInvalidPath("me..name");
		testParseInvalidPath("me.friends]");
		testParseInvalidPath("me.friends[[");
		testParseInvalidPath("me.friends[.");
		testParseInvalidPath("me.friends[]");
		testParseInvalidPath("me.friends[5]name");
		testParseInvalidPath("me.friends[5]]");
	}

	private static void testParseInvalidPath(String path) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> MapGraphQlResponse.parseFieldPath(path))
				.withMessage("Invalid path: '" + path + "'");
	}

	@Test
	void fieldValue() throws Exception {

		// null "data"
		testFieldValue("", "null", null);
		testFieldValue("me", "null", MapGraphQlResponse.NO_VALUE);

		// no such key or index
		testFieldValue("me", "{}", MapGraphQlResponse.NO_VALUE); // "data" not null but no such key
		testFieldValue("me.friends", "{\"me\":{}}", MapGraphQlResponse.NO_VALUE);
		testFieldValue("me.friends[0]", "{\"me\": {\"friends\": []}}", MapGraphQlResponse.NO_VALUE);

		// nest within map or list
		testFieldValue("me.name", "{\"me\":{\"name\":\"Luke\"}}", "Luke");
		testFieldValue("me.friends[1].name", "{\"me\": {\"friends\": [{\"name\": \"Luke\"}, {\"name\": \"Yoda\"}]}}", "Yoda");
	}

	@SuppressWarnings("unchecked")
	private static void testFieldValue(String path, String json, @Nullable Object expected) throws IOException {
		List<Object> parsedPath = MapGraphQlResponse.parseFieldPath(path);
		Map<String, Object> map = mapper.readValue(json, Map.class);
		MapGraphQlResponse response = new MapGraphQlResponse(Collections.singletonMap("data", map));
		Object value = response.getFieldValue(parsedPath);
		if (expected != null) {
			assertThat(value).isEqualTo(expected);
		}
		else {
			assertThat(value).isNotNull();
		}
	}

	@Test
	void fieldValueInvalidPath() throws Exception {
		testFieldValueInvalidPath("me.name", "{\"me\": []}");
		testFieldValueInvalidPath("me.name", "{\"me\": \"string\"}");
		testFieldValueInvalidPath("me.friends[0]", "{\"me\": {\"friends\": {}}}");
		testFieldValueInvalidPath("me.friends[0]", "{\"me\": {\"friends\": {\"name\":\"Luke\"}}}");
	}

	@SuppressWarnings("unchecked")
	private static void testFieldValueInvalidPath(String path, String json) throws IOException {
		List<Object> parsedPath = MapGraphQlResponse.parseFieldPath(path);
		Map<String, Object> map = mapper.readValue(json, Map.class);
		MapGraphQlResponse response = new MapGraphQlResponse(Collections.singletonMap("data", map));

		assertThatIllegalArgumentException().isThrownBy(() -> response.getFieldValue(parsedPath))
				.withMessage("Invalid path " + parsedPath + ", data: " + map);
	}

	@Test
	void fieldErrors() {

		List<Object> path = MapGraphQlResponse.parseFieldPath("me.friends");

		GraphQLError error0 = createError(null, "fail-me");
		GraphQLError error1 = createError("/me", "fail-me");
		GraphQLError error2 = createError("/me/friends", "fail-me-friends");
		GraphQLError error3 = createError("/me/friends[0]/name", "fail-me-friends-name");

		List<Map<String, Object>> errorList =
				Stream.of(error0, error1, error2, error3)
						.map(GraphQLError::toSpecification).collect(Collectors.toList());

		MapGraphQlResponse response = new MapGraphQlResponse(Collections.singletonMap("errors", errorList));
		List<GraphQLError> errors = response.getFieldErrors(path);

		assertThat(errors).containsExactly(error1, error2, error3);
	}

	private GraphQLError createError(@Nullable String errorPath, String message) {
		GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError().message(message);
		if (errorPath != null) {
			builder = builder.path(ResultPath.parse(errorPath));
		}
		Map<String, Object> errorMap = builder.build().toSpecification();
		return new MapGraphQlError(errorMap);
	}

}

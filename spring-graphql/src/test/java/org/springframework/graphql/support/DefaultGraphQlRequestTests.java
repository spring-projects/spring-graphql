/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.support;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.graphql.GraphQlRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultGraphQlRequest}.
 * @author Brian Clozel
 */
class DefaultGraphQlRequestTests {

	@Test
	void requestAsMapShouldContainAllEntries() {

		String document = "query HeroNameAndFriends($episode: Episode) {" +
				"  hero(episode: $episode) {" +
				"    name"
				+ "  }" +
				"}";

		Map<String, Object> variables = Map.of("episode", "JEDI");
		Map<String, Object> extensions = Map.of("myExtension", "value");

		GraphQlRequest request = new DefaultExecutionGraphQlRequest(
				document, "HeroNameAndFriends", variables, extensions, "1", null);

		assertThat(request.toMap())
				.containsEntry("query", document)
				.containsEntry("operationName", "HeroNameAndFriends")
				.containsEntry("variables", variables)
				.containsEntry("extensions", extensions);
	}

}

/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.support;

import java.util.Collections;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BearerTokenAuthenticationExtractorTests}.
 *
 * @author Rossen Stoyanchev
 */
class BearerTokenAuthenticationExtractorTests {

	private static final BearerTokenAuthenticationExtractor extractor = new BearerTokenAuthenticationExtractor();


	@Test
	void extract() {
		Authentication auth = getAuthentication("Bearer 123456789");

		assertThat(auth).isNotNull();
		assertThat(auth.getName()).isEqualTo("123456789");
	}

	@Test // gh-1116
	void extractCaseInsensitive() {
		Authentication auth = getAuthentication(Map.of("authorization", "Bearer 123456789"));

		assertThat(auth).isNotNull();
		assertThat(auth.getName()).isEqualTo("123456789");
	}

	@Test
	void noToken() {
		Authentication auth = getAuthentication(Collections.emptyMap());
		assertThat(auth).isNull();
	}

	@Test
	void notBearerToken() {
		assertThatThrownBy(() -> getAuthentication("abc"))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.hasMessage("Not a bearer token");
	}

	@Test
	void invalidToken() {
		assertThatThrownBy(() -> getAuthentication("Bearer ???"))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.hasMessage("Bearer token is malformed");
	}

	private static @Nullable Authentication getAuthentication(String value) {
		return getAuthentication(Map.of(BearerTokenAuthenticationExtractor.AUTHORIZATION_KEY, value));
	}

	private static @Nullable Authentication getAuthentication(Map<String, Object> map) {
		return extractor.getAuthentication(map).block();
	}

}

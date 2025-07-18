/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.server.support;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.lang.Nullable;
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

	@Nullable
	private static Authentication getAuthentication(String value) {
		return getAuthentication(Map.of(BearerTokenAuthenticationExtractor.AUTHORIZATION_KEY, value));
	}

	@Nullable
	private static Authentication getAuthentication(Map<String, Object> map) {
		return extractor.getAuthentication(map).block();
	}

}

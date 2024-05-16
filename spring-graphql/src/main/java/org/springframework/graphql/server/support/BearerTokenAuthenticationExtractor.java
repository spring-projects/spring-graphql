/*
 * Copyright 2002-2024 the original author or authors.
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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reactor.core.publisher.Mono;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.util.StringUtils;

/**
 * {@link AuthenticationExtractor} that extracts a
 * <a href="https://datatracker.ietf.org/doc/html/rfc6750#section-1.2">bearer token</a>.
 *
 * @author Joshua Cummings
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public final class BearerTokenAuthenticationExtractor implements AuthenticationExtractor {

	private static final Pattern authorizationPattern =
			Pattern.compile("^Bearer (?<token>[a-zA-Z0-9-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);


	private final String authorizationKey;


	/**
	 * Constructor that defaults the payload key to use to "Authorization".
	 */
	public BearerTokenAuthenticationExtractor() {
		this("Authorization");
	}

	/**
	 * Constructor with the key for the authorization value.
	 * @param authorizationKey the key under which to look up the authorization
	 * value in the {@code "connection_init"} payload.
	 */
	public BearerTokenAuthenticationExtractor(String authorizationKey) {
		this.authorizationKey = authorizationKey;
	}


	@Override
	public Mono<Authentication> getAuthentication(Map<String, Object> payload) {
		String authorizationValue = (String) payload.get(this.authorizationKey);
		if (!StringUtils.startsWithIgnoreCase(authorizationValue, "bearer")) {
			return Mono.empty();
		}

		Matcher matcher = authorizationPattern.matcher(authorizationValue);
		if (matcher.matches()) {
			String token = matcher.group("token");
			return Mono.just(new BearerTokenAuthenticationToken(token));
		}

		BearerTokenError error = BearerTokenErrors.invalidToken("Bearer token is malformed");
		return Mono.error(new OAuth2AuthenticationException(error));
	}

}

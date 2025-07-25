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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
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

	/** Default key to access Authorization value in {@code connection_init} payload. */
	public static final String AUTHORIZATION_KEY = "Authorization";

	private static final Pattern authorizationPattern =
			Pattern.compile("^Bearer (?<token>[a-zA-Z0-9-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);


	private final String authorizationKey;


	/**
	 * Constructor that defaults to {@link #AUTHORIZATION_KEY} for the payload key.
	 */
	public BearerTokenAuthenticationExtractor() {
		this(AUTHORIZATION_KEY);
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
		String authorizationValue = getAuthorizationValue(payload);
		if (authorizationValue == null) {
			return Mono.empty();
		}

		if (!StringUtils.startsWithIgnoreCase(authorizationValue, "bearer")) {
			BearerTokenError error = BearerTokenErrors.invalidRequest("Not a bearer token");
			return Mono.error(new OAuth2AuthenticationException(error));
		}

		Matcher matcher = authorizationPattern.matcher(authorizationValue);
		if (!matcher.matches()) {
			BearerTokenError error = BearerTokenErrors.invalidToken("Bearer token is malformed");
			return Mono.error(new OAuth2AuthenticationException(error));
		}

		String token = matcher.group("token");
		return Mono.just(new BearerTokenAuthenticationToken(token));
	}

	private @Nullable String getAuthorizationValue(Map<String, Object> payload) {
		String value = (String) payload.get(this.authorizationKey);
		if (value != null) {
			return value;
		}
		for (String key : payload.keySet()) {
			if (key.equalsIgnoreCase(this.authorizationKey)) {
				return (String) payload.get(key);
			}
		}
		return null;
	}

}

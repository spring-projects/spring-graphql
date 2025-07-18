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

package org.springframework.graphql.server.webflux;


import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.graphql.server.WebGraphQlInterceptor.Chain;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.graphql.server.support.BearerTokenAuthenticationExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.graphql.server.support.BearerTokenAuthenticationExtractor.AUTHORIZATION_KEY;

/**
 * Unit tests for {@link AuthenticationWebSocketInterceptor}.
 *
 * @author Rossen Stoyanchev
 */
class AuthenticationWebSocketInterceptorTests {

	private static final String ATTRIBUTE_KEY = AuthenticationWebSocketInterceptor.class.getName() + ".AUTHENTICATION";


	private final ReactiveAuthenticationManager authenticationManager = mock(ReactiveAuthenticationManager.class);

	private final AuthenticationWebSocketInterceptor interceptor =
			new AuthenticationWebSocketInterceptor(new BearerTokenAuthenticationExtractor(), this.authenticationManager);

	private final WebSocketSessionInfo sessionInfo = mock(WebSocketSessionInfo.class);


	@Test
	void intercept() {
		Map<String, Object> attributes = new HashMap<>();
		given(this.sessionInfo.getAttributes()).willReturn(attributes);

		TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", "credentials");
		given(this.authenticationManager.authenticate(any())).willReturn(Mono.just(authentication));


		this.interceptor.handleConnectionInitialization(
				this.sessionInfo, Map.of(AUTHORIZATION_KEY, "Bearer 123456789")).block();

		assertThat(attributes).containsExactly(Map.entry(ATTRIBUTE_KEY, new SecurityContextImpl(authentication)));


		WebSocketGraphQlRequest request = new WebSocketGraphQlRequest(
				URI.create("/path"), new HttpHeaders(), null, null, Collections.emptyMap(),
				Map.of("query", "{}"), "1", null, this.sessionInfo);

		Map<Object, Object> savedContext = new HashMap<>();
		Chain chain = r -> Mono.deferContextual((contextView) -> {
			contextView.forEach(savedContext::put);
			return Mono.empty();
		});


		this.interceptor.intercept(request, chain).block();

		Mono<SecurityContext> mono = (Mono<SecurityContext>) savedContext.get(SecurityContext.class);
		assertThat(mono.block().getAuthentication()).isEqualTo(authentication);
	}

}

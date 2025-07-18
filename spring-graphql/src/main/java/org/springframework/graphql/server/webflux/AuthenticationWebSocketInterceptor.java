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

import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.graphql.server.support.AbstractAuthenticationWebSocketInterceptor;
import org.springframework.graphql.server.support.AuthenticationExtractor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

/**
 * Extension of {@link AbstractAuthenticationWebSocketInterceptor} for use with
 * the WebFlux GraphQL transport.
 *
 * @author Joshua Cummings
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public final class AuthenticationWebSocketInterceptor extends AbstractAuthenticationWebSocketInterceptor {

	private final ReactiveAuthenticationManager authenticationManager;


	public AuthenticationWebSocketInterceptor(
			AuthenticationExtractor extractor, ReactiveAuthenticationManager manager) {

		super(extractor);
		this.authenticationManager = manager;
	}

	@Override
	protected Mono<Authentication> authenticate(Authentication authentication) {
		return this.authenticationManager.authenticate(authentication);
	}

	@Override
	protected ContextView getContextToWrite(SecurityContext securityContext) {
		return ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));
	}

}


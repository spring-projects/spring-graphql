/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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


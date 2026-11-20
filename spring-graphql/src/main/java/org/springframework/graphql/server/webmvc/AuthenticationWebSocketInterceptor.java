/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.server.webmvc;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.graphql.server.support.AbstractAuthenticationWebSocketInterceptor;
import org.springframework.graphql.server.support.AuthenticationExtractor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

/**
 * Extension of {@link AbstractAuthenticationWebSocketInterceptor} for use with
 * the WebMVC GraphQL transport.
 *
 * @author Joshua Cummings
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public final class AuthenticationWebSocketInterceptor extends AbstractAuthenticationWebSocketInterceptor {

	private final AuthenticationManager authenticationManager;


	public AuthenticationWebSocketInterceptor(
			AuthenticationExtractor authExtractor, AuthenticationManager authManager) {

		super(authExtractor);
		this.authenticationManager = authManager;
	}

	@Override
	protected Mono<Authentication> authenticate(Authentication authentication) {
		return Mono.just(this.authenticationManager.authenticate(authentication));
	}

	@Override
	protected ContextView getContextToWrite(SecurityContext securityContext) {
		String key = SecurityContext.class.getName(); // match SecurityContextThreadLocalAccessor key
		return Context.of(key, securityContext);
	}

}


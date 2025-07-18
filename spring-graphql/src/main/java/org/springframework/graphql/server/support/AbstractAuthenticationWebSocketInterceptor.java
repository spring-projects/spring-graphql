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

import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Base class for interceptors that extract an {@link Authentication} from
 * the payload of a {@code "connection_init"} GraphQL over WebSocket message.
 * The authentication is saved in WebSocket attributes from where it is later
 * accessed and propagated to subsequent {@code "subscribe"} messages.
 *
 * @author Joshua Cummings
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public abstract class AbstractAuthenticationWebSocketInterceptor implements WebSocketGraphQlInterceptor {

	private final String authenticationAttribute = getClass().getName() + ".AUTHENTICATION";


	private final AuthenticationExtractor authenticationExtractor;


	/**
	 * Constructor with the strategy to use to extract the authentication value
	 * from the {@code "connection_init"} message.
	 * @param authExtractor the extractor to use
	 */
	public AbstractAuthenticationWebSocketInterceptor(AuthenticationExtractor authExtractor) {
		this.authenticationExtractor = authExtractor;
	}

	@Override
	public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo info, Map<String, Object> payload) {
		return this.authenticationExtractor.getAuthentication(payload)
				.flatMap(this::authenticate)
				.doOnNext((authentication) -> {
					SecurityContext securityContext = new SecurityContextImpl(authentication);
					info.getAttributes().put(this.authenticationAttribute, securityContext);
				})
				.then(Mono.empty());
	}

	/**
	 * Subclasses implement this method to return an authenticated
	 * {@link SecurityContext} or an error.
	 * @param authentication the authentication value extracted from the payload
	 */
	protected abstract Mono<Authentication> authenticate(Authentication authentication);

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		if (!(request instanceof WebSocketGraphQlRequest webSocketRequest)) {
			return chain.next(request);
		}
		Map<String, Object> attributes = webSocketRequest.getSessionInfo().getAttributes();
		SecurityContext securityContext = (SecurityContext) attributes.get(this.authenticationAttribute);
		if (securityContext != null) {
			ContextView contextView = getContextToWrite(securityContext);
			return chain.next(request).contextWrite(contextView);
		}
		return chain.next(request);
	}

	/**
	 * Subclasses implement this to decide how to insert the {@link SecurityContext}
	 * into the Reactor context of the {@link WebSocketGraphQlInterceptor} chain.
	 * @param securityContext the {@code SecurityContext} to write to the context
	 */
	protected abstract ContextView getContextToWrite(SecurityContext securityContext);

}


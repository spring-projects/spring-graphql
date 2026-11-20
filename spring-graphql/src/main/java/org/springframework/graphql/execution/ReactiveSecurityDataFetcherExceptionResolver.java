/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.util.Collections;
import java.util.List;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * Reactive
 * {@link org.springframework.graphql.execution.DataFetcherExceptionResolver}
 * for Spring Security exceptions. For use in applications with a reactive
 * transport (e.g. WebFlux HTTP endpoint).
 *
 * @author Rob Winch
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class ReactiveSecurityDataFetcherExceptionResolver implements DataFetcherExceptionResolver {

	private AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();


	/**
	 * Set the resolver to use to check if an authentication is anonymous that
	 * in turn determines whether {@code AccessDeniedException} is classified
	 * as "unauthorized" or "forbidden".
	 * @param trustResolver the resolver to use
	 */
	public void setAuthenticationTrustResolver(AuthenticationTrustResolver trustResolver) {
		this.trustResolver = trustResolver;
	}


	@Override
	public Mono<List<GraphQLError>> resolveException(Throwable ex, DataFetchingEnvironment environment) {
		if (ex instanceof AuthenticationException) {
			GraphQLError error = SecurityExceptionResolverUtils.resolveUnauthorized(environment);
			return Mono.just(Collections.singletonList(error));
		}
		if (ex instanceof AccessDeniedException) {
			return ReactiveSecurityContextHolder.getContext()
					.map((context) -> Collections.singletonList(
							SecurityExceptionResolverUtils.resolveAccessDenied(environment, this.trustResolver, context)))
					.switchIfEmpty(Mono.fromCallable(() -> Collections.singletonList(
							SecurityExceptionResolverUtils.resolveUnauthorized(environment))));
		}
		return Mono.empty();
	}

}

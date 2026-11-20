/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link org.springframework.graphql.execution.DataFetcherExceptionResolver}
 * for Spring Security exceptions. For use in applications with a non-reactive
 * transport (e.g. Spring MVC HTTP endpoint).
 *
 * @author Rob Winch
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class SecurityDataFetcherExceptionResolver extends DataFetcherExceptionResolverAdapter {

	private AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();


	public SecurityDataFetcherExceptionResolver() {
		setThreadLocalContextAware(true);
	}


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
	protected @Nullable GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment environment) {
		if (ex instanceof AuthenticationException) {
			return SecurityExceptionResolverUtils.resolveUnauthorized(environment);
		}
		if (ex instanceof AccessDeniedException) {
			SecurityContext securityContext = SecurityContextHolder.getContext();
			return SecurityExceptionResolverUtils.resolveAccessDenied(environment, this.trustResolver, securityContext);
		}
		return null;
	}

}

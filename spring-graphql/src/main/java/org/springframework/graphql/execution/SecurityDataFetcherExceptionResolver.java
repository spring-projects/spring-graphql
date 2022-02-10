/*
 * Copyright 2002-2022 the original author or authors.
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
package org.springframework.graphql.execution;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;

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
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment environment) {
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

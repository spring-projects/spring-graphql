/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import java.security.Principal;

import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolver to obtain {@link Principal} from Spring Security context via
 * {@link SecurityContext#getAuthentication()}.
 *
 * <p>The resolver checks both ThreadLocal context via {@link SecurityContextHolder}
 * for Spring MVC applications, and {@link ReactiveSecurityContextHolder} for
 * Spring WebFlux applications. It returns .
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class PrincipalMethodArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * Return "true" if the argument is {@link Principal} or a subtype.
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return Principal.class.isAssignableFrom(parameter.getParameterType());
	}


	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {
		return resolveAuthentication(parameter);
	}

	@SuppressWarnings("NullAway") // https://github.com/uber/NullAway/issues/1290
	static Object resolveAuthentication(MethodParameter parameter) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null) {
			return auth;
		}

		Mono<Authentication> authMono =
				ReactiveSecurityContextHolder.getContext().mapNotNull(SecurityContext::getAuthentication);

		if (isRequired(parameter)) {
			authMono = authMono.switchIfEmpty(
					Mono.error(new AuthenticationCredentialsNotFoundException("No Authentication")));
		}

		return authMono;
	}

	private static boolean isRequired(MethodParameter parameter) {
		return (!parameter.isOptional() && !Mono.class.isAssignableFrom(parameter.getParameterType()));
	}

}

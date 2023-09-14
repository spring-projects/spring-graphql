/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import java.security.Principal;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Mono;

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

	static Object resolveAuthentication(MethodParameter parameter) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null) {
			return auth;
		}

		Mono<Authentication> authMono =
				ReactiveSecurityContextHolder.getContext().mapNotNull(SecurityContext::getAuthentication);

		if (!parameter.isOptional()) {
			authMono = authMono.switchIfEmpty(
					Mono.error(new AuthenticationCredentialsNotFoundException("No Authentication")));
		}

		return authMono;
	}

}

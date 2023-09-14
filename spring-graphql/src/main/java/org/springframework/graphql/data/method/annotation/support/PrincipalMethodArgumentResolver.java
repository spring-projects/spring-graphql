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
import java.util.function.Function;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.core.MethodParameter;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
	 * Return "true" if the argument is {@link Principal} or a sub-type.
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return Principal.class.isAssignableFrom(parameter.getParameterType());
	}


	@Override
	public Object resolveArgument(MethodParameter parameter, DataFetchingEnvironment environment) {
		return doResolve(parameter.isOptional());
	}

	static Object doResolve(boolean optional) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication != null) {
			return authentication;
		}

		return ReactiveSecurityContextHolder.getContext()
				.switchIfEmpty(optional ? Mono.empty() : Mono.error(new AuthenticationCredentialsNotFoundException("SecurityContext not available")))
				.handle((context, sink) -> {
					Authentication auth = context.getAuthentication();

					if (auth != null) {
						sink.next(auth);
					} else if (!optional) {
						sink.error(new AuthenticationCredentialsNotFoundException("An Authentication object was not found in the SecurityContext"));
					} else {
						sink.complete();
					}
				});
	}

}

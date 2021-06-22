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
package io.spring.sample.graphql;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;

// @formatter:off

@Component
public class SecurityDataFetcherExceptionResolver implements DataFetcherExceptionResolver {

	private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();


	@Override
	public Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment) {
		if (exception instanceof AuthenticationException) {
			return unauthorized(environment);
		}
		if (exception instanceof AccessDeniedException) {
			return ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.filter(a -> !this.authenticationTrustResolver.isAnonymous(a))
				.flatMap(anonymous -> forbidden(environment))
				.switchIfEmpty(unauthorized(environment));
		}
		return Mono.empty();
	}

	public void setAuthenticationTrustResolver(AuthenticationTrustResolver authenticationTrustResolver) {
		Assert.notNull(authenticationTrustResolver, "authenticationTrustResolver cannot be null");
		this.authenticationTrustResolver = authenticationTrustResolver;
	}

	private Mono<List<GraphQLError>> unauthorized(DataFetchingEnvironment environment) {
		return Mono.fromCallable(() -> Collections.singletonList(
				GraphqlErrorBuilder.newError(environment)
						.errorType(ErrorType.UNAUTHORIZED)
						.message("Unauthorized")
						.build()));
	}

	private Mono<List<GraphQLError>> forbidden(DataFetchingEnvironment environment) {
		return Mono.fromCallable(() -> Collections.singletonList(
				GraphqlErrorBuilder.newError(environment)
						.errorType(ErrorType.FORBIDDEN)
						.message("Forbidden")
						.build()));
	}

}

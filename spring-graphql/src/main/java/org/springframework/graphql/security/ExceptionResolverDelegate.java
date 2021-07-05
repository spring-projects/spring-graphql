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
package org.springframework.graphql.security;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.Assert;

/**
 * Package private delegate class shared by the reactive and non-reactive resolver types.
 *
 * @author Rossen Stoyanchev
 */
class ExceptionResolverDelegate {

	private AuthenticationTrustResolver resolver = new AuthenticationTrustResolverImpl();


	public void setAuthenticationTrustResolver(AuthenticationTrustResolver resolver) {
		Assert.notNull(resolver, "AuthenticationTrustResolver is required");
		this.resolver = resolver;
	}

	public GraphQLError resolveUnauthorized(DataFetchingEnvironment environment) {
		return GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.UNAUTHORIZED)
				.message("Unauthorized")
				.build();
	}

	public GraphQLError resolveAccessDenied(DataFetchingEnvironment env, SecurityContext securityContext) {
		return this.resolver.isAnonymous(securityContext.getAuthentication()) ?
				resolveUnauthorized(env) :
				GraphqlErrorBuilder.newError(env)
						.errorType(ErrorType.FORBIDDEN)
						.message("Forbidden")
						.build();
	}

}

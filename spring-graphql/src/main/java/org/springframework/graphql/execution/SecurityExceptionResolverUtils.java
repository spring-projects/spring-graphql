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
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.core.context.SecurityContext;

/**
 * Package private delegate class shared by the reactive and non-reactive resolver types.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
class SecurityExceptionResolverUtils {

	static GraphQLError resolveUnauthorized(DataFetchingEnvironment environment) {
		return GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.UNAUTHORIZED)
				.message("Unauthorized")
				.build();
	}

	static GraphQLError resolveAccessDenied(
			DataFetchingEnvironment env, AuthenticationTrustResolver resolver, SecurityContext securityContext) {

		return resolver.isAnonymous(securityContext.getAuthentication()) ?
				resolveUnauthorized(env) :
				GraphqlErrorBuilder.newError(env)
						.errorType(ErrorType.FORBIDDEN)
						.message("Forbidden")
						.build();
	}

}

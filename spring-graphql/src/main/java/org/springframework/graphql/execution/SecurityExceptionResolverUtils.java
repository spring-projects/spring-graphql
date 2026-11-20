/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
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
 */
final class SecurityExceptionResolverUtils {

	private SecurityExceptionResolverUtils() {

	}

	static GraphQLError resolveUnauthorized(DataFetchingEnvironment environment) {
		return GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.UNAUTHORIZED)
				.message("Unauthorized")
				.build();
	}

	@SuppressWarnings("NullAway") // until https://github.com/spring-projects/spring-security/issues/17764
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

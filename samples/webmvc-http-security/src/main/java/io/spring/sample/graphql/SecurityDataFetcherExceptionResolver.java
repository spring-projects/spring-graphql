package io.spring.sample.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class SecurityDataFetcherExceptionResolver extends DataFetcherExceptionResolverAdapter {

	private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		if (ex instanceof AuthenticationException) {
			return unauthorized(env);
		}
		if (ex instanceof AccessDeniedException) {
			SecurityContext context = SecurityContextHolder.getContext();
			Authentication authentication = context.getAuthentication();
			if (this.authenticationTrustResolver.isAnonymous(authentication)) {
				return unauthorized(env);
			}
			return forbidden(env);
		}
		return null;
	}

	public void setAuthenticationTrustResolver(AuthenticationTrustResolver authenticationTrustResolver) {
		Assert.notNull(authenticationTrustResolver, "authenticationTrustResolver cannot be null");
		this.authenticationTrustResolver = authenticationTrustResolver;
	}

	private GraphQLError unauthorized(DataFetchingEnvironment environment) {
		return GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.UNAUTHORIZED)
				.message("Unauthorized")
				.build();
	}

	private GraphQLError forbidden(DataFetchingEnvironment environment) {
		return GraphqlErrorBuilder.newError(environment)
						.errorType(ErrorType.FORBIDDEN)
						.message("Forbidden")
						.build();
	}

}

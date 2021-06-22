package io.spring.sample.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.execution.SyncDataFetcherExceptionResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;

@Component
public class SecurityDataFetcherExceptionResolver implements SyncDataFetcherExceptionResolver {

	private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

	@Override
	public List<GraphQLError> doResolveException(Throwable exception, DataFetchingEnvironment environment) {
		if (exception instanceof AuthenticationException) {
			return unauthorized(environment);
		}
		if (exception instanceof AccessDeniedException) {
			SecurityContext context = SecurityContextHolder.getContext();
			Authentication authentication = context.getAuthentication();
			if (this.authenticationTrustResolver.isAnonymous(authentication)) {
				return unauthorized(environment);
			}
			return forbidden(environment);
		}
		return null;
	}

	public void setAuthenticationTrustResolver(AuthenticationTrustResolver authenticationTrustResolver) {
		Assert.notNull(authenticationTrustResolver, "authenticationTrustResolver cannot be null");
		this.authenticationTrustResolver = authenticationTrustResolver;
	}

	private List<GraphQLError> unauthorized(DataFetchingEnvironment environment) {
		return Arrays.asList(
				GraphqlErrorBuilder.newError(environment)
						.errorType(ErrorType.UNAUTHORIZED)
						.message("Unauthorized")
						.build());
	}

	private List<GraphQLError> forbidden(DataFetchingEnvironment environment) {
		return Arrays.asList(
				GraphqlErrorBuilder.newError(environment)
						.errorType(ErrorType.FORBIDDEN)
						.message("Forbidden")
						.build());
	}

}

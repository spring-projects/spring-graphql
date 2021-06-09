package io.spring.sample.graphql;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
public class SecurityDataFetcherExceptionResolver implements DataFetcherExceptionResolver {
	private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

	@Override
	public Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment) {
		if (exception instanceof AuthenticationException) {

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

	private Mono<List<GraphQLError>> unauthorized(DataFetchingEnvironment environment) {
		return Mono.fromCallable(() -> Arrays.asList(GraphqlErrorBuilder.newError(environment).errorType(ErrorType.UNAUTHORIZED).message("Unauthorized").build()));
	}

	private Mono<List<GraphQLError>> forbidden(DataFetchingEnvironment environment) {
		return Mono.fromCallable(() -> Arrays.asList(GraphqlErrorBuilder.newError(environment).errorType(ErrorType.FORBIDDEN).message("Forbidden").build()));
	}
}

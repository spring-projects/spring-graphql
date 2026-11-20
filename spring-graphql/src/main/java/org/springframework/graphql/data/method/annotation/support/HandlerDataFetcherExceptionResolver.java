/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import java.util.List;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.graphql.execution.DataFetcherExceptionResolver;

/**
 * Extension of {@link DataFetcherExceptionResolver} with overloaded method to
 * apply at the point of DataFetcher invocation to allow local exception handling.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public interface HandlerDataFetcherExceptionResolver extends DataFetcherExceptionResolver {


	@Override
	default Mono<List<GraphQLError>> resolveException(Throwable exception, DataFetchingEnvironment environment) {
		return resolveException(exception, environment, null);
	}

	/**
	 * Resolve an exception raised by the given handler.
	 * @param ex the exception to resolve
	 * @param environment the environment for the invoked {@code DataFetcher}
	 * @param handler the handler that raised the exception, if applicable
	 * @return a {@code Mono} with resolved {@code GraphQLError}s as specified in
	 * {@link DataFetcherExceptionResolver#resolveException(Throwable, DataFetchingEnvironment)}
	 */
	Mono<List<GraphQLError>> resolveException(
			Throwable ex, DataFetchingEnvironment environment, @Nullable Object handler);

}

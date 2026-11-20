/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.ExecutionId;
import graphql.schema.DataFetchingEnvironment;
import io.micrometer.context.ContextSnapshot;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;

/**
 * {@link DataFetcherExceptionHandler} that invokes {@link DataFetcherExceptionResolver}'s
 * in a sequence until one returns a list of {@link GraphQLError}'s.
 *
 * <p>Use {@link DataFetcherExceptionResolver#createExceptionHandler(List)} to
 * create an instance.
 *
 * @author Rossen Stoyanchev
 */
class ExceptionResolversExceptionHandler implements DataFetcherExceptionHandler {

	private static final Log logger = LogFactory.getLog(ExceptionResolversExceptionHandler.class);


	private final List<DataFetcherExceptionResolver> resolvers;


	/**
	 * Create an instance.
	 * @param resolvers the resolvers to use
	 */
	ExceptionResolversExceptionHandler(List<DataFetcherExceptionResolver> resolvers) {
		Assert.notNull(resolvers, "'resolvers' is required");
		this.resolvers = new ArrayList<>(resolvers);
	}


	@Override
	@SuppressWarnings("NullAway")
	public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
			DataFetcherExceptionHandlerParameters handlerParameters) {

		Throwable exception = unwrapException(handlerParameters);
		DataFetchingEnvironment env = handlerParameters.getDataFetchingEnvironment();
		ContextSnapshot snapshot = ContextPropagationHelper.captureFrom(env.getGraphQlContext());
		try {
			return Flux.fromIterable(this.resolvers)
					.flatMap((resolver) -> resolver.resolveException(exception, env))
					.map((errors) -> DataFetcherExceptionHandlerResult.newResult().errors(errors).build())
					.next()
					.doOnNext((result) -> logResolvedException(exception, result))
					.onErrorResume((resolverEx) -> Mono.just(handleResolverError(resolverEx, exception, env)))
					.switchIfEmpty(Mono.fromCallable(() -> createInternalError(exception, env)))
					.contextWrite(snapshot::updateContext)
					.toFuture();
		}
		catch (Exception resolverEx) {
			return CompletableFuture.completedFuture(handleResolverError(resolverEx, exception, env));
		}
	}

	private Throwable unwrapException(DataFetcherExceptionHandlerParameters params) {
		Throwable ex = params.getException();
		if (ex instanceof CompletionException completionException) {
			return (completionException.getCause() != null) ? completionException.getCause() : completionException;
		}
		return ex;
	}

	private void logResolvedException(Throwable ex, DataFetcherExceptionHandlerResult result) {
		if (logger.isDebugEnabled()) {
			String name = ex.getClass().getSimpleName();
			logger.debug("Resolved " + name + " to GraphQL error(s): " + result.getErrors(), ex);
		}
	}

	private DataFetcherExceptionHandlerResult handleResolverError(
			Throwable resolverException, Throwable originalException, DataFetchingEnvironment env) {

		if (logger.isWarnEnabled()) {
			logger.warn("Failure while resolving " + originalException.getMessage(), resolverException);
		}
		return createInternalError(originalException, env);
	}

	private DataFetcherExceptionHandlerResult createInternalError(Throwable ex, DataFetchingEnvironment env) {
		ExecutionId executionId = env.getExecutionId();
		if (logger.isErrorEnabled()) {
			logger.error("Unresolved " + ex.getClass().getSimpleName() + " for executionId " + executionId, ex);
		}
		return DataFetcherExceptionHandlerResult
				.newResult(GraphqlErrorBuilder.newError(env)
						.errorType(ErrorType.INTERNAL_ERROR)
						.message(ErrorType.INTERNAL_ERROR + " for " + executionId)
						.build())
				.build();
	}

}

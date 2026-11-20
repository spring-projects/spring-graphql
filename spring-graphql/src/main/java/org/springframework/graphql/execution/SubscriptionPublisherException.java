/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.jspecify.annotations.Nullable;

import org.springframework.core.NestedRuntimeException;

/**
 * An exception raised after a GraphQL subscription
 * {@link org.reactivestreams.Publisher} ends with an exception, and after that
 * exception has been resolved to GraphQL errors.
 *
 * <p>The underlying transport, e.g. WebSocket, can handle a
 * {@link SubscriptionPublisherException} and send a final error message to the
 * client with the list of GraphQL errors.
 *
 * @author Mykyta Ivchenko
 * @author Rossen Stoyanchev
 * @since 1.0.1
 */
@SuppressWarnings("serial")
public final class SubscriptionPublisherException extends NestedRuntimeException {

	private final List<GraphQLError> errors;


	/**
	 * Constructor with the resolved GraphQL errors and the original exception
	 * from the GraphQL subscription {@link org.reactivestreams.Publisher}.
	 * @param errors the list of resolved GraphQL errors
	 * @param cause the original exception
	 */
	public SubscriptionPublisherException(List<GraphQLError> errors, @Nullable Throwable cause) {
		super("GraphQL subscription ended with error(s): " + errors, cause);
		this.errors = errors;
	}


	/**
	 * Return the GraphQL errors the exception was resolved to by the configured
	 * {@link SubscriptionExceptionResolver}'s. These errors can be included in
	 * an error message to be sent to the client by the underlying transport.
	 */
	public List<GraphQLError> getErrors() {
		return this.errors;
	}

	/**
	 * Return an {@link ExecutionResult} specification map with the GraphQL errors.
	 * @since 1.3.0
	 */
	public Map<String, Object> toMap() {
		return ExecutionResult.newExecutionResult().errors(this.errors).build().toSpecification();
	}

}

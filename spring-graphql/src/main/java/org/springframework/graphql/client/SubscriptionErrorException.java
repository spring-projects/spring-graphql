/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;

import java.util.List;

import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.ResponseError;

/**
 * WebSocket {@link GraphQlTransportException} raised when a subscription
 * ends with an {@code "error"} message. The {@link #getErrors()} method provides
 * access to the GraphQL errors from the message payload.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class SubscriptionErrorException extends GraphQlTransportException {

	private final List<ResponseError> errors;


	/**
	 * Constructor with the request details and the errors listed in the payload
	 * of the {@code "errors"} message.
	 * @param request the request details
	 * @param errors the errors listed in the payload
	 */
	public SubscriptionErrorException(GraphQlRequest request, List<ResponseError> errors) {
		super("GraphQL subscription completed with an \"error\" message, " +
				"with the following errors: " + errors, null, request);
		this.errors = errors;
	}


	/**
	 * Return the errors contained in the GraphQL over WebSocket "errors" message.
	 */
	public List<ResponseError> getErrors() {
		return this.errors;
	}

}

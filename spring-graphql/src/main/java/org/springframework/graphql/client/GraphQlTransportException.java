/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.client;


import org.jspecify.annotations.Nullable;

import org.springframework.graphql.GraphQlRequest;

/**
 * Exception raised by a {@link GraphQlTransport} or used to wrap an exception
 * from a {@code GraphQlTransport} implementation.
 *
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class GraphQlTransportException extends GraphQlClientException {

	/**
	 * Constructor with a default message.
	 * @param cause the original cause of the transport error
	 * @param request the request that failed at the transport level
	 */
	public GraphQlTransportException(@Nullable Throwable cause, GraphQlRequest request) {
		super("GraphQlTransport error" + ((cause != null) ? ": " + cause.getMessage() : ""), cause, request);
	}

	/**
	 * Constructor with a given message.
	 * @param message the exception message to use
	 * @param cause the original cause of the transport error
	 * @param request the request that failed at the transport level
	 */
	public GraphQlTransportException(String message, @Nullable Throwable cause, GraphQlRequest request) {
		super(message, cause, request);
	}

}
